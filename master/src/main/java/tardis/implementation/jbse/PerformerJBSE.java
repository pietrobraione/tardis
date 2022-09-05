package tardis.implementation.jbse;

import static jbse.apps.run.JAVA_MAP_Utils.mapModelAssumptionViolated;
import static tardis.implementation.common.Util.filterOnPattern;
import static tardis.implementation.common.Util.shorten;
import static tardis.implementation.common.Util.stringifyPostFrontierPathCondition;
import static tardis.implementation.common.Util.stringifyTestPathCondition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jbse.algo.exc.CannotManageStateException;
import jbse.apps.run.UninterpretedNoContextException;
import jbse.bc.ClassHierarchy;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.common.exc.ClasspathException;
import jbse.common.exc.InvalidInputException;
import jbse.dec.exc.DecisionException;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.mem.Clause;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.ClauseAssumeReferenceSymbolic;
import jbse.mem.HeapObjekt;
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.FrozenStateException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.val.ReferenceSymbolic;
import tardis.Coverage;
import tardis.Options;
import tardis.framework.InputBuffer;
import tardis.framework.Performer;
import tardis.framework.PerformerPausableFixedThreadPoolExecutor;
import tardis.implementation.data.JBSEResultInputOutputBuffer;
import tardis.implementation.data.TreePath;
import tardis.implementation.evosuite.EvosuiteResult;
import tardis.implementation.evosuite.TestCase;

/**
 * A {@link Performer} that consumes {@link EvosuiteResult}s by invoking JBSE
 * to build path conditions from tests. Upon success the produced path conditions 
 * are emitted as {@link JBSEResult}s.
 * 
 * @author Pietro Braione
 */
public final class PerformerJBSE extends PerformerPausableFixedThreadPoolExecutor<EvosuiteResult, JBSEResult> {
    private static final Logger LOGGER = LogManager.getFormatterLogger(PerformerJBSE.class);
    
    private static final int NUM_INPUTS_PER_JOB = 1;
    
    private final Options o;
    private final JBSEResultInputOutputBuffer out;
    private final TreePath treePath;
    private final ConcurrentHashMap<String, State> initialStateCache = new ConcurrentHashMap<>();
    private final AtomicLong pathCoverage = new AtomicLong(0);
    private final ConcurrentHashMap<MethodPathConditon, Set<String>> freshObjectsExpansions = new ConcurrentHashMap<>();

    public PerformerJBSE(Options o, InputBuffer<EvosuiteResult> in, JBSEResultInputOutputBuffer out, TreePath treePath) {
        super("PerformerJBSE", in, out, o.getNumOfThreadsJBSE(), NUM_INPUTS_PER_JOB, o.getThrottleFactorJBSE(), o.getTimeoutJBSEJobCreationDuration() / NUM_INPUTS_PER_JOB, o.getTimeoutJBSEJobCreationUnit());
        this.o = o.clone();
        this.out = out;
        this.treePath = treePath;
    }

    @Override
    protected final Runnable makeJob(List<EvosuiteResult> items) {
        final EvosuiteResult item = items.get(0);
        final Runnable job = () -> explore(item, item.getStartDepth());
        return job;
    }

    /**
     * Executes a test case and generates tests for all the alternative branches
     * starting from some depth up to some maximum depth.
     * 
     * @param item a {@link EvosuiteResult}.
     * @param depthStart the depth to which generation of tests must be started.
     */
    private void explore(EvosuiteResult item, int depthStart) {
        if (this.o.getMaxDepth() <= 0) {
            return;
        }
        try (final RunnerPath rp = new RunnerPath(this.o, item, possiblyGetInitialStateCached(item))) {
            final TestCase tc = item.getTestCase();
            
            //runs the test case up to the final state, and takes the 
            //final state's path condition
            final State stateFinal;
        	try {
        		stateFinal = rp.runProgram();
        	} catch (UninterpretedNoContextException e) {
                LOGGER.info("Skipped test case %s because it invokes an uninterpreted function in the context of a model", tc.getClassName());
                return;
        	}
            if (stateFinal == null) {
                //the execution violated some assumption: prints some feedback
                LOGGER.info("Run test case %s, the test case violated an assumption or exhausted a bound before arriving at the final state", tc.getClassName());
                return;
            }
            final String entryPoint = item.getTargetMethodSignature();
            final List<Clause> pathConditionFinal = stateFinal.getPathCondition();

            //prints some feedback
            LOGGER.info("Run test case %s, path condition %s:%s", tc.getClassName(), entryPoint, stringifyTestPathCondition(pathConditionFinal));
            
            //compares pathConditionFinal with the generating path condition, and
            //complains if the former does not refine the latter
            if (!refines(pathConditionFinal, item.getPathConditionGenerating())) {
            	LOGGER.warn("Test case %s diverges from generating path condition %s", tc.getClassName(), stringifyPostFrontierPathCondition(item.getPathConditionGenerating()));
            }            
            
            //skips the test case if its path was already covered,
            //otherwise records its path and calculates coverage
            final Set<String> coveredBranches = rp.getCoverage();
            final Set<String> newCoveredBranches;
            final int branchCoverage;
            final int branchCoverageTarget;
            final int branchCoverageUnsafe;
            synchronized (this.treePath) {
                if (this.treePath.containsPath(entryPoint, pathConditionFinal, true)) {
                    LOGGER.info("Test case %s redundant, skipped", tc.getClassName());
                    return;
                }
                newCoveredBranches = this.treePath.insertPath(entryPoint, pathConditionFinal, coveredBranches, Collections.emptySet(), true);
        		branchCoverage = this.treePath.totalCovered();
        		branchCoverageTarget = this.treePath.totalCovered(this.o.patternBranchesTarget());
        		branchCoverageUnsafe = this.treePath.totalCovered(this.o.patternBranchesUnsafe());
            }
        	final long pathCoverage = this.pathCoverage.incrementAndGet();

            //emits coverage feedback
        	LOGGER.info("Current coverage: %d path%s, %d branch%s (total), %d branch%s (target), %d failed assertion%s", pathCoverage, (pathCoverage == 1 ? "" : "s"), branchCoverage, (branchCoverage == 1 ? "" : "es"), branchCoverageTarget, (branchCoverageTarget == 1 ? "" : "es"), branchCoverageUnsafe, (branchCoverageUnsafe == 1 ? "" : "s"));
            
            //possibly caches the initial state
            final State stateInitial = rp.getStateInitial();
            possiblySetInitialStateCached(item, stateInitial);
            
            //learns the new data for future update of indices
            learnDataForIndices(newCoveredBranches, coveredBranches, entryPoint, pathConditionFinal);
            
            //updates all indices and reclassifies all the items in output buffer
            //TODO possibly do it more lazily!
            updateIndicesAndReclassify();

            //emits the test if it covers something new
            emitTestIfCoversSomethingNew(item, newCoveredBranches);
            
            //reruns the test case at all the depths in the range, generates all the modified 
            //path conditions and puts all the output jobs in the output queue
            final int depthFinal = Math.min(depthStart + this.o.getMaxTestCaseDepth(), stateFinal.getDepth());
            try {
				createOutputJobsForFrontiersAtAllDepths(rp, item, tc, stateInitial, stateFinal, depthStart, depthFinal);
			} catch (InterruptedException e) {
				//the performer shut down
				return;
			}
        } catch (NoTargetHitException e) {
            //prints some feedback
            LOGGER.warn("Run test case %s, does not reach the target method %s", item.getTestCase().getClassName(), item.getTargetMethodSignature());
        } catch (DecisionException | CannotBuildEngineException | InitializationException |
                InvalidClassFileFactoryClassException | NonexistingObservedVariablesException |
                ClasspathException | CannotBacktrackException | CannotManageStateException |
                ThreadStackEmptyException | ContradictionException | EngineStuckException |
                FailureException | FrozenStateException e) {
            LOGGER.error("Unexpected frozen state exception while trying to generate path condition for additional fresh object");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
		}
    }
    
    private static boolean refines(List<Clause> possiblyRefining, List<Clause> possiblyRefined) {
        if (possiblyRefined == null) {
        	return true;
        }
        final List<Clause> possiblyRefiningShortened = shorten(possiblyRefining);
        final ListIterator<Clause> itPossiblyRefiningShortened = possiblyRefiningShortened.listIterator();
        final List<Clause> possiblyRefinedShortened = shorten(possiblyRefined);
        for (Clause cExpected: possiblyRefinedShortened) {
        	if (!itPossiblyRefiningShortened.hasNext()) {
        		return false;
        	}
        	final Clause cActual = itPossiblyRefiningShortened.next();
        	if (cExpected instanceof ClauseAssumeExpandsSubtypes) {
        		if (!(cActual instanceof ClauseAssumeExpands)) {
        			return false;
        		} else {
        			final ClauseAssumeExpandsSubtypes caExpected = (ClauseAssumeExpandsSubtypes) cExpected;
        			final ClauseAssumeExpands caActual = (ClauseAssumeExpands) cActual;
        			if (!caExpected.getReference().equals(caActual.getReference())) {
        				return false;
        			}
        			final String actualExpansion = caActual.getObjekt().getType().getClassName();
        			for (String forbiddenExpansion : caExpected.forbiddenExpansions()) {
        				if (forbiddenExpansion.equals(actualExpansion)) {
        					return false;
        				}
        			}
        		}
        	} else if (!cExpected.equals(cActual)) {
        		return false;
        	}
        }
        return true;
    }
    
    private State possiblyGetInitialStateCached(EvosuiteResult item) {
        final String key = item.getTargetMethodSignature();
        final State value = this.initialStateCache.get(key);
        return (value == null ? null : value.clone());
    }
    
    private synchronized void possiblySetInitialStateCached(EvosuiteResult item, State initialState) {
        final String key = item.getTargetMethodSignature();
        if (this.initialStateCache.containsKey(key)) {
            return;
        } else {
            this.initialStateCache.put(key, initialState.clone());
        }
    }
    
    private void learnDataForIndices(Set<String> newCoveredBranches, Set<String> coveredBranches, String entryPoint, List<Clause> pathConditionFinal) {
        if (this.o.getUseIndexImprovability()) {
        	this.out.learnCoverageForIndexImprovability(newCoveredBranches);
        }
        if (this.o.getUseIndexNovelty()) {
        	this.out.learnCoverageForIndexNovelty(coveredBranches);
        }
        if (this.o.getUseIndexInfeasibility()) {
        	this.out.learnPathConditionForIndexInfeasibility(entryPoint, pathConditionFinal, true);
        }
    }
    
    private void updateIndicesAndReclassify() {
        if (this.o.getUseIndexImprovability()) {
        	this.out.updateIndexImprovabilityAndReclassify();
        }
        if (this.o.getUseIndexNovelty()) { 
        	this.out.updateIndexNoveltyAndReclassify();
        }
        if (this.o.getUseIndexInfeasibility()) {
        	this.out.updateIndexInfeasibilityAndReclassify();
        }
    }
    
    private void emitTestIfCoversSomethingNew(EvosuiteResult item, Set<String> newCoveredBranches) {
        final TestCase tc = item.getTestCase();
        final Coverage coverageType = this.o.getCoverage();
        final int branchCoverageTargetNew = filterOnPattern(newCoveredBranches, this.o.patternBranchesTarget()).size();            
        final int branchCoverageUnsafeNew = filterOnPattern(newCoveredBranches, this.o.patternBranchesUnsafe()).size();
        if (coverageType == Coverage.PATHS || 
        (coverageType == Coverage.BRANCHES && branchCoverageTargetNew > 0) ||
        (coverageType == Coverage.UNSAFE && branchCoverageUnsafeNew > 0)) {
            //more feedback
            if (coverageType == Coverage.BRANCHES) {
                LOGGER.info("Test case %s covered branch%s %s", tc.getClassName(), (branchCoverageTargetNew == 1 ? " " : "es"), String.join(", ", newCoveredBranches));
            }
            
            //copies the test in out
            try {
                //gets the source and destination paths of the test source file
                final Path source = item.getTestCase().getSourcePath();
                final Path destination = this.o.getOutDirectory().resolve(item.getTestCase().getClassName() + ".java");
                
                //creates the intermediate package directories if they do not exist
                final int lastSlash = item.getTestCase().getClassName().lastIndexOf('/');
                if (lastSlash != -1) {
                    final String dirs = item.getTestCase().getClassName().substring(0, lastSlash);
                    final Path destinationDir = this.o.getOutDirectory().resolve(dirs);
                    Files.createDirectories(destinationDir);
                }
                
                //copies the test file
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);

                //possibly copies the scaffolding file
                final Path sourceScaffolding = item.getTestCase().getScaffoldingPath();
                if (sourceScaffolding != null) {
                    final Path destinationScaffolding = this.o.getOutDirectory().resolve(item.getTestCase().getClassName() + "_scaffolding.java");
                    Files.copy(sourceScaffolding, destinationScaffolding, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error while attempting to copy test case %s or its scaffolding to its destination directory", tc.getClassName());
                LOGGER.error("Message: %s", e.toString());
                LOGGER.error("Stack trace:");
                for (StackTraceElement elem : e.getStackTrace()) {
                    LOGGER.error("%s", elem.toString());
                }
                //falls through
            }
        }
    }
    
    private void createOutputJobsForFrontiersAtAllDepths(RunnerPath rp, EvosuiteResult item, TestCase tc, State stateInitial, State stateFinal, int depthStart, int depthFinal) 
    throws DecisionException, CannotBuildEngineException, InitializationException, InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
    ClasspathException, CannotBacktrackException, CannotManageStateException, ThreadStackEmptyException, ContradictionException, EngineStuckException, 
    FailureException, InterruptedException, FrozenStateException {
        boolean noOutputJobGenerated = true;
        for (int depthCurrent = depthStart; depthCurrent <= depthFinal; ++depthCurrent) {
        	try {
        		final List<State> statesPostFrontier = rp.runProgram(depthCurrent);

        		//checks shutdown of the performer
        		if (Thread.interrupted()) {
        			throw new InterruptedException();
        		}

        		//creates all the output jobs
        		noOutputJobGenerated = createOutputJobsForFrontier(rp, statesPostFrontier, item, tc, stateInitial, stateFinal, depthCurrent) && noOutputJobGenerated;
        	} catch (UninterpretedNoContextException e) {
                LOGGER.info("From test case %s stopping generation of path conditions at depth %d because of invocation of an uninterpreted function in the context of a model", tc.getClassName(), depthCurrent);
                break;
        	}
        }
        if (noOutputJobGenerated) {
            LOGGER.info("From test case %s no path condition generated", tc.getClassName());
        }
    }
    
    private boolean createOutputJobsForFrontier(RunnerPath rp, List<State> statesPostFrontier, EvosuiteResult item, TestCase tc, State stateInitial, State stateFinal, int depthCurrent) 
    throws FrozenStateException {
        //gives some feedback if detects a contradiction
        if (statesPostFrontier.isEmpty()) {
            LOGGER.info("Test case %s, detected contradiction while generating path conditions at depth %d", tc.getClassName(), depthCurrent);
        }

        final String entryPoint = item.getTargetMethodSignature();
    	boolean noOutputJobGenerated = true;
        final State statePreFrontier = rp.getStatePreFrontier();
        final List<String> branchesPostFrontier = rp.getBranchesPostFrontier(); 
        for (int i = 0; i < statesPostFrontier.size(); ++i) {
        	//gets the generated path condition
            final State statePostFrontier = statesPostFrontier.get(i);
            final List<Clause> pathConditionPostFrontier = statePostFrontier.getPathCondition();
            final Clause pathConditionPostFrontierLastClause = pathConditionPostFrontier.get(pathConditionPostFrontier.size() - 1);
            
            //determines if the last clause is an expands one
            final boolean lastClauseIsExpands = !pathConditionPostFrontier.isEmpty() && (pathConditionPostFrontierLastClause instanceof ClauseAssumeExpands);
            
            //creates the generated path condition
            final List<Clause> pathConditionGenerated = new ArrayList<>(pathConditionPostFrontier);
            final Set<String> expansions;
            if (lastClauseIsExpands) {
            	final ReferenceSymbolic referenceToExpand = ((ClauseAssumeExpands) pathConditionPostFrontierLastClause).getReference();

            	//gets/creates the set of the seen expansions for the path condition
            	final MethodPathConditon methodPathCondition = new MethodPathConditon(entryPoint, shorten(pathConditionGenerated));
        		if (!this.freshObjectsExpansions.containsKey(methodPathCondition)) {
        			this.freshObjectsExpansions.put(methodPathCondition, Collections.synchronizedSet(new HashSet<>()));
        		}
        		expansions = this.freshObjectsExpansions.get(methodPathCondition);
        		
        		//finds the clause in the test path condition that predicates
        		//on the same symbolic reference as the last clause in the 
        		//generated path condition
            	Clause pathConditionFinalClause = null;
            	for (Clause clause : stateFinal.getPathCondition()) {
            		if (clause instanceof ClauseAssumeReferenceSymbolic && 
            		((ClauseAssumeReferenceSymbolic) clause).getReference().equals(referenceToExpand)) {
            			pathConditionFinalClause = clause;
            			break;
            		}
            	}
            	
        		//if such clause is also an expands clause, puts its expansion 
            	//in the set of the seen expansions for the path condition
            	if (pathConditionFinalClause instanceof ClauseAssumeExpands) {
            		final ClauseAssumeExpands pathConditionFinalClauseExpands = (ClauseAssumeExpands) pathConditionFinalClause;
            		final HeapObjekt objectTest = stateFinal.getObject(pathConditionFinalClauseExpands.getReference());
                	//skips the generated path condition if it contradicts
                	//the current seen expansions
            		final HeapObjekt objectPathCondition = ((ClauseAssumeExpands) pathConditionPostFrontierLastClause).getObjekt();
            		if (objectPathCondition.getType().equals(objectTest.getType())) {
                		expansions.add(objectTest.getType().getClassName());
            		} else {
            			continue;
            		}
            	} //else, leave the set of the seen expansions as it is

            	//sets the last clause of the generated path condition
            	final Clause pathConditionGeneratedLastClause;
            	try {
					pathConditionGeneratedLastClause = new ClauseAssumeExpandsSubtypes(referenceToExpand, expansions);
				} catch (InvalidInputException e) {
					//this should never happen
	                LOGGER.error("Unexpected InvalidInputException while attempting to generate an unconstrained expansion path condition clause");
	                LOGGER.error("Message: %s", e.toString());
	                LOGGER.error("Stack trace:");
	                for (StackTraceElement elem : e.getStackTrace()) {
	                    LOGGER.error("%s", elem.toString());
	                }
	                continue;
				}
            	pathConditionGenerated.set(pathConditionGenerated.size() - 1, pathConditionGeneratedLastClause);
            } else {
            	expansions = Collections.emptySet();
            }
            
        	//inserts the generated path condition in the treePath 
            //it if is not already present and if it does not violate 
            //some basic assumptions on model maps, otherwise skips
            synchronized (this.treePath) {
            	if (this.treePath.containsPath(entryPoint, pathConditionGenerated, false)) {
            		LOGGER.info("From test case %s skipping generated post-frontier path condition %s:%s because redundant", tc.getClassName(), entryPoint, stringifyPostFrontierPathCondition(pathConditionGenerated));
            		continue;
            	}
            	final ClassHierarchy hier = statePostFrontier.getClassHierarchy();
            	if (!pathConditionGenerated.isEmpty() && mapModelAssumptionViolated(hier, pathConditionPostFrontierLastClause)) {
            		LOGGER.info("From test case %s skipping generated post-frontier path condition %s:%s because clause %s contradicts initialMap assumptions", tc.getClassName(), entryPoint, stringifyPostFrontierPathCondition(pathConditionGenerated), pathConditionGenerated.get(pathConditionGenerated.size() - 1));
            		continue;
            	}
            	this.treePath.insertPath(entryPoint, pathConditionGenerated, rp.getCoverage(), branchesPostFrontier, false);
            }
            
            //creates the output job...
            final boolean atJump = rp.getAtJump();
            final Map<Long, String> stringLiterals = rp.getStringLiterals().get(i);
            final Set<Long> stringOthers = rp.getStringOthers().get(i); 
            final JBSEResult output = 
            new JBSEResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), 
                           stateInitial, statePreFrontier, statePostFrontier, pathConditionGenerated, atJump, 
                           (atJump ? branchesPostFrontier.get(i) : null), stringLiterals, stringOthers, 
                           (lastClauseIsExpands ? depthCurrent - 1 : depthCurrent), expansions);

            //...and emits it in the output buffer
            this.out.add(output);
            LOGGER.info("From test case %s generated post-frontier path condition %s:%s%s", tc.getClassName(), entryPoint, stringifyPostFrontierPathCondition(output), (atJump ? (" aimed at branch " + branchesPostFrontier.get(i)) : ""));
            noOutputJobGenerated = false;
        }
        return noOutputJobGenerated;
    }
}

