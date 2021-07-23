package tardis.implementation;

import static jbse.apps.run.JAVA_MAP_Utils.assumptionViolated;
import static tardis.implementation.Util.filterOnPattern;
import static tardis.implementation.Util.shorten;
import static tardis.implementation.Util.stringifyPathCondition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jbse.algo.exc.CannotManageStateException;
import jbse.apps.run.UninterpretedNoContextException;
import jbse.bc.ClassHierarchy;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.common.exc.ClasspathException;
import jbse.dec.exc.DecisionException;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.mem.Clause;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.Objekt;
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.FrozenStateException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.val.ReferenceSymbolic;
import tardis.Coverage;
import tardis.Options;
import tardis.framework.InputBuffer;
import tardis.framework.Performer;

/**
 * A {@link Performer} that consumes {@link EvosuiteResult}s by invoking JBSE
 * to build path conditions from tests. Upon success the produced path conditions 
 * are emitted as {@link JBSEResult}s.
 * 
 * @author Pietro Braione
 */
public final class PerformerJBSE extends Performer<EvosuiteResult, JBSEResult> {
    private static final Logger LOGGER = LogManager.getFormatterLogger(PerformerJBSE.class);
    
    private final Options o;
    private final JBSEResultInputOutputBuffer out;
    private final TreePath treePath;
    private final ConcurrentHashMap<String, State> initialStateCache = new ConcurrentHashMap<>();
    private final AtomicLong pathCoverage = new AtomicLong(0);
    private final ConcurrentHashMap<ReferenceSymbolic, JBSEResult> freshObjectsJBSEResults = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ReferenceSymbolic, List<String>> freshObjectsExpansions = new ConcurrentHashMap<>();

    public PerformerJBSE(Options o, InputBuffer<EvosuiteResult> in, JBSEResultInputOutputBuffer out, TreePath treePath) {
        super(in, out, o.getNumOfThreadsJBSE(), 1, o.getThrottleFactorJBSE(), o.getGlobalTimeBudgetDuration(), o.getGlobalTimeBudgetUnit());
        this.o = o.clone();
        this.out = out;
        this.treePath = treePath;
    }

    @Override
    protected final Runnable makeJob(List<EvosuiteResult> items) {
        final EvosuiteResult item = items.get(0);
        final Runnable job = () -> {
            try {
                explore(item, item.getStartDepth());
            } catch (DecisionException | CannotBuildEngineException | InitializationException |
            InvalidClassFileFactoryClassException | NonexistingObservedVariablesException |
            ClasspathException | CannotBacktrackException | CannotManageStateException |
            ThreadStackEmptyException | ContradictionException | EngineStuckException |
            FailureException e ) {
                LOGGER.error("Unexpected error while exploring test case %s", item.getTestCase().getClassName());
                LOGGER.error("Message: %s", e.toString());
                LOGGER.error("Stack trace:");
                for (StackTraceElement elem : e.getStackTrace()) {
                    LOGGER.error("%s", elem.toString());
                }
            }
        };
        return job;
    }

    /**
     * Executes a test case and generates tests for all the alternative branches
     * starting from some depth up to some maximum depth.
     * 
     * @param item a {@link EvosuiteResult}.
     * @param depthStart the depth to which generation of tests must be started.
     * @throws DecisionException
     * @throws CannotBuildEngineException
     * @throws InitializationException
     * @throws InvalidClassFileFactoryClassException
     * @throws NonexistingObservedVariablesException
     * @throws ClasspathException
     * @throws CannotBacktrackException
     * @throws CannotManageStateException
     * @throws ThreadStackEmptyException
     * @throws ContradictionException
     * @throws EngineStuckException
     * @throws FailureException
     */
    private void explore(EvosuiteResult item, int depthStart) 
    throws DecisionException, CannotBuildEngineException, InitializationException, 
    InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
    ClasspathException, CannotBacktrackException, CannotManageStateException, 
    ThreadStackEmptyException, ContradictionException, EngineStuckException, 
    FailureException {
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
            LOGGER.info("Run test case %s, path condition %s", tc.getClassName(), stringifyPathCondition(shorten(pathConditionFinal)));
            
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
            
            //emits an additional path condition for a new fresh object, in case the test
            //has as a last clause an expands clause
            createOutputJobForAdditionalFreshObjectClause(rp, depthFinal, item, stateFinal);
        } catch (NoTargetHitException e) {
            //prints some feedback
            LOGGER.warn("Run test case %s, does not reach the target method %s:%s:%s", item.getTestCase().getClassName(), item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName());
        } catch (FrozenStateException e) {
            LOGGER.error("Unexpected frozen state exception while trying to generate path condition for additional fresh object");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
		}
    }
    
    private State possiblyGetInitialStateCached(EvosuiteResult item) {
        final String key = item.getTargetMethodClassName() + ":" + item.getTargetMethodDescriptor() + ":" + item.getTargetMethodName();
        final State value = this.initialStateCache.get(key);
        return (value == null ? null : value.clone());
    }
    
    private synchronized void possiblySetInitialStateCached(EvosuiteResult item, State initialState) {
        final String key = item.getTargetMethodClassName() + ":" + item.getTargetMethodDescriptor() + ":" + item.getTargetMethodName();
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
    FailureException, InterruptedException {
        boolean noOutputJobGenerated = true;
        for (int depthCurrent = depthStart; depthCurrent <= depthFinal; ++depthCurrent) {
        	try {
        		final List<State> statesPostFrontier = rp.runProgram(depthCurrent);

        		//checks shutdown of the performer
        		if (Thread.interrupted()) {
        			throw new InterruptedException();
        		}

        		//creates all the output jobs
        		noOutputJobGenerated = createOutputJobsForFrontier(rp, statesPostFrontier, item, tc, stateInitial, depthCurrent) && noOutputJobGenerated;
        	} catch (UninterpretedNoContextException e) {
                LOGGER.info("From test case %s skipping a path condition because it invokes an uninterpreted function in the context of a model", tc.getClassName());
        	}
        }
        if (noOutputJobGenerated) {
            LOGGER.info("From test case %s no path condition generated", tc.getClassName());
        }
    }
    
    private boolean createOutputJobsForFrontier(RunnerPath rp, List<State> statesPostFrontier, EvosuiteResult item, TestCase tc, State stateInitial, int depthCurrent) {
        //gives some feedback if detects a contradiction
        if (statesPostFrontier.isEmpty()) {
            LOGGER.info("Test case %s, detected contradiction while generating path conditions at depth %d", tc.getClassName(), depthCurrent);
        }

        final String entryPoint = item.getTargetMethodSignature();
    	boolean noOutputJobGenerated = true;
        final State statePreFrontier = rp.getStatePreFrontier();
        final List<String> branchesPostFrontier = rp.getBranchesPostFrontier(); 
        for (int i = 0; i < statesPostFrontier.size(); ++i) {
            final State statePostFrontier = statesPostFrontier.get(i);
            final List<Clause> pathCondition = statePostFrontier.getPathCondition();
            synchronized (this.treePath) {
            	if (this.treePath.containsPath(entryPoint, pathCondition, false)) {
            		continue;
            	}
            	final ClassHierarchy hier = statePostFrontier.getClassHierarchy();
            	if (!pathCondition.isEmpty() && assumptionViolated(hier, pathCondition.get(pathCondition.size() - 1))) {
            		LOGGER.info("From test case %s skipping path condition due to violated assumption %s on initialMap in path condition %s", tc.getClassName(), pathCondition.get(pathCondition.size() - 1), stringifyPathCondition(shorten(pathCondition)));
            		continue;
            	}

            	//inserts the generated path in the treePath
            	this.treePath.insertPath(entryPoint, pathCondition, rp.getCoverage(), branchesPostFrontier, false);
            }

            //creates the output job
            final boolean atJump = rp.getAtJump();
            final Map<Long, String> stringLiterals = rp.getStringLiterals().get(i);
            final Set<Long> stringOthers = rp.getStringOthers().get(i); 
            final JBSEResult output = 
            new JBSEResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), 
                           stateInitial, statePreFrontier, statePostFrontier, atJump, 
                           (atJump ? branchesPostFrontier.get(i) : null), stringLiterals, stringOthers, depthCurrent);
            
            //if the last clause is an expands path condition, saves the expanded 
            //reference data so it may generate fresh objects in the future
            if (!pathCondition.isEmpty() && (pathCondition.get(pathCondition.size() - 1) instanceof ClauseAssumeExpands)) {
            	final ClauseAssumeExpands clauseLast = (ClauseAssumeExpands) pathCondition.get(pathCondition.size() - 1);
            	final ReferenceSymbolic clauseLastReference = clauseLast.getReference();
        		this.freshObjectsJBSEResults.put(clauseLastReference, output);
            }
            
            //emits the output job in the output buffer
            this.out.add(output);
            LOGGER.info("From test case %s generated path condition %s%s", tc.getClassName(), stringifyPathCondition(shorten(pathCondition)), (atJump ? (" aimed at branch " + branchesPostFrontier.get(i)) : ""));
            noOutputJobGenerated = false;
        }
        return noOutputJobGenerated;
    }
    
    private void createOutputJobForAdditionalFreshObjectClause(RunnerPath rp, int depthFinal, EvosuiteResult item, State stateFinal) 
    throws FrozenStateException, DecisionException, CannotBuildEngineException, InitializationException, 
    InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, ClasspathException, 
    CannotBacktrackException, CannotManageStateException, ThreadStackEmptyException, ContradictionException, 
    EngineStuckException, FailureException {
    	final TestCase tc = item.getTestCase();
    	final State postFrontierState = item.getPostFrontierState();
    	
    	//gets the path condition that generated the test: if the test is seed
    	//(i.e., postFrontierState == null), then we use the path condition of
    	//the final state of the test itself, otherwise we use the path condition
    	//of the post-frontier that was used to generate the test
    	final List<Clause> pathConditionFinal = (postFrontierState == null ? stateFinal.getPathCondition() : postFrontierState.getPathCondition());
    	
    	if (!pathConditionFinal.isEmpty() && (pathConditionFinal.get(pathConditionFinal.size() - 1) instanceof ClauseAssumeExpands)) {
    		final ClauseAssumeExpands clauseLast = (ClauseAssumeExpands) pathConditionFinal.get(pathConditionFinal.size() - 1);
    		final ReferenceSymbolic clauseLastReference = clauseLast.getReference();

    		//adds the current test case expansion to the set of the seen expansions 
    		if (!this.freshObjectsExpansions.containsKey(clauseLastReference)) {
    			this.freshObjectsExpansions.put(clauseLastReference, Collections.synchronizedList(new ArrayList<>()));
    		}
    		final List<String> expansions = this.freshObjectsExpansions.get(clauseLastReference);
    		final Objekt objekt = stateFinal.getObject(clauseLastReference);
    		expansions.add(objekt.getType().getClassName());

    		final JBSEResult output;
			final State statePostFrontier;
			final boolean atJump;
			final String targetBranch;
    		if (this.freshObjectsJBSEResults.containsKey(clauseLastReference)) {
    			//gets the previous JBSE result
    			final JBSEResult jbseResult = this.freshObjectsJBSEResults.get(clauseLastReference);
    			final String targetMethodClassName = jbseResult.getTargetMethodClassName();
    			final String targetMethodDescriptor = jbseResult.getTargetMethodDescriptor();
    			final String targetMethodName = jbseResult.getTargetMethodName();
    			final State stateInitial = jbseResult.getInitialState();
    			final State statePreFrontier = jbseResult.getPreFrontierState();
    			statePostFrontier = jbseResult.getPostFrontierState();
    			atJump = jbseResult.getAtJump();
    			targetBranch = jbseResult.getTargetBranch();
    			final Map<Long, String> stringLiterals = jbseResult.getStringLiterals();
    			final Set<Long> stringOthers = jbseResult.getStringOthers();
    			final int depth = jbseResult.getDepth();
    			output =
    			new JBSEResult(targetMethodClassName, targetMethodDescriptor, targetMethodName, 
    			               stateInitial, statePreFrontier, statePostFrontier, atJump,
    			               targetBranch, stringLiterals, stringOthers, depth, 
    			               expansions);
    		} else {
    			//the current test is a seed test, therefore there are
    			//no previous JBSE results: builds a JBSE result from 
    			//the post-frontier state
    			
    			//first, finds the post-frontier state
    			if (depthFinal == 0) {
    				//this is a seed test with a trivial path condition {ROOT}:this expands and nothing else 
    				return;
    			}
    			final List<State> statesPostFrontier = rp.runProgram(depthFinal);
    			int i;
    			boolean found = false;
    			for (i = 0; i < statesPostFrontier.size(); ++i) {
    				final State s = statesPostFrontier.get(i);
    				final List<Clause> pathCondition = s.getPathCondition();
    				final Clause lastPathConditionClause = pathCondition.get(pathCondition.size() - 1);
    				if (lastPathConditionClause instanceof ClauseAssumeExpands) {
    					found = true;
    					break;
    				}
    			}
    			if (!found) {
    				return; //this should never happen, however gracefully exiting should be ok
    			}
    			
    			final String targetMethodClassName = item.getTargetMethodClassName();
    			final String targetMethodDescriptor = item.getTargetMethodDescriptor();
    			final String targetMethodName = item.getTargetMethodName();
    			final State stateInitial = rp.getStateInitial();
    			final State statePreFrontier = rp.getStatePreFrontier();
				statePostFrontier = statesPostFrontier.get(i);
    			atJump = rp.getAtJump();
				targetBranch = (atJump ? rp.getBranchesPostFrontier().get(i) : null);
    			final Map<Long, String> stringLiterals = rp.getStringLiterals().get(i);
    			final Set<Long> stringOthers = rp.getStringOthers().get(i);
    			final int depth = depthFinal;
    			output =
    			new JBSEResult(targetMethodClassName, targetMethodDescriptor, targetMethodName, 
    			               stateInitial, statePreFrontier, statePostFrontier, atJump,
    			               targetBranch, stringLiterals, stringOthers, depth, 
    			               expansions);
    		}
    		this.out.add(output);
    		LOGGER.info("From test case %s generated path condition %s%s%s", tc.getClassName(), stringifyPathCondition(shorten(statePostFrontier.getPathCondition())), (expansions.isEmpty() ? "" : (" excluded " + expansions.stream().collect(Collectors.joining(", ")))),  (atJump ? (" aimed at branch " + targetBranch) : ""));
        }
    }
}

