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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jbse.algo.exc.CannotManageStateException;
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
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.ThreadStackEmptyException;
import sushi.configure.Coverage;
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
    private final HashMap<String, State> initialStateCache = new HashMap<>();
    private AtomicLong pathCoverage = new AtomicLong(0);

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
    
    private State possiblyGetInitialStateCached(EvosuiteResult item) {
        final String key = item.getTargetMethodClassName() + ":" + item.getTargetMethodDescriptor() + ":" + item.getTargetMethodName();
        final State value = this.initialStateCache.get(key);
        return (value == null ? null : value.clone());
    }
    
    private void possiblySetInitialStateCached(EvosuiteResult item, State initialState) {
        final String key = item.getTargetMethodClassName() + ":" + item.getTargetMethodDescriptor() + ":" + item.getTargetMethodName();
        if (this.initialStateCache.containsKey(key)) {
            return;
        } else {
            this.initialStateCache.put(key, initialState.clone());
        }
    }

    /**
     * Executes a test case and generates tests for all the alternative branches
     * starting from some depth up to some maximum depth.
     * 
     * @param item a {@link EvosuiteResult}.
     * @param startDepth the depth to which generation of tests must be started.
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
    private void explore(EvosuiteResult item, int startDepth) 
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
            final State tcFinalState = rp.runProgram();
            if (tcFinalState == null) {
                //the execution violated some assumption: prints some feedback
                LOGGER.info("Run test case %s, the test case violated an assumption", tc.getClassName());
                return;
            }
            final List<Clause> tcFinalPC = tcFinalState.getPathCondition();

            //prints some feedback
            LOGGER.info("Run test case %s, path condition %s", tc.getClassName(), stringifyPathCondition(shorten(tcFinalPC)));
            
            //skips the test case if its path was already covered,
            //otherwise records its path
            final Set<String> coveredBranches = rp.getCoverage();
            final Set<String> newCoveredBranches;
            synchronized (this.treePath) {
                if (this.treePath.containsPath(tcFinalPC, true)) {
                    LOGGER.info("Test case %s redundant, skipped", tc.getClassName());
                    return;
                }
                newCoveredBranches = this.treePath.insertPath(tcFinalPC, coveredBranches, Collections.emptySet(), true);
            }

            //possibly caches the initial state
            final State initialState = rp.getInitialState();
            possiblySetInitialStateCached(item, initialState);
            
            //learns the new data for future update of indices
            if (this.o.getUseIndexImprovability()) {
            	this.out.learnCoverageForIndexImprovability(newCoveredBranches);
            }
            if (this.o.getUseIndexNovelty()) {
            	this.out.learnCoverageForIndexNovelty(coveredBranches);
            }
            if (this.o.getUseIndexInfeasibility()) {
            	this.out.learnPathConditionForIndexInfeasibility(tcFinalPC, true);
            }
            
            //updates all indices and reclassifies all the items in output buffer
            //TODO possibly do it more lazily!
            if (this.o.getUseIndexImprovability()) {
            	this.out.updateIndexImprovabilityAndReclassify();
            }
            if (this.o.getUseIndexNovelty()) { 
            	this.out.updateIndexNoveltyAndReclassify();
            }
            if (this.o.getUseIndexInfeasibility()) {
            	this.out.updateIndexInfeasibilityAndReclassify();
            }

            //emits the test if it covers something new
            final Coverage coverage = this.o.getCoverage();
            final int branchCoverageTargetNew = filterOnPattern(newCoveredBranches, this.o.patternBranchesTarget()).size();            
            final int branchCoverageUnsafeNew = filterOnPattern(newCoveredBranches, this.o.patternBranchesUnsafe()).size();
            if (coverage == Coverage.PATHS || 
            (coverage == Coverage.BRANCHES && branchCoverageTargetNew > 0) ||
            (coverage == Coverage.UNSAFE && branchCoverageUnsafeNew > 0)) {
                //more feedback
                if (coverage == Coverage.BRANCHES) {
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
            
            //calculates coverage and emits feedback
            final long pathCoverage = this.pathCoverage.incrementAndGet();
            final int branchCoverage = this.treePath.totalCovered();
            final int branchCoverageTarget = this.treePath.totalCovered(this.o.patternBranchesTarget());
            final int branchCoverageUnsafe = this.treePath.totalCovered(this.o.patternBranchesUnsafe());
            LOGGER.info("Current coverage: %d path%s, %d branch%s (total), %d branch%s (target), %d failed assertion%s", pathCoverage, (pathCoverage == 1 ? "" : "s"), branchCoverage, (branchCoverage == 1 ? "" : "es"), branchCoverageTarget, (branchCoverageTarget == 1 ? "" : "es"), branchCoverageUnsafe, (branchCoverageUnsafe == 1 ? "" : "s"));
            
            //reruns the test case at all the depths in the range, generates all the modified 
            //path conditions and gathers the necessary information to produce the output jobs
            final int tcFinalDepth = Math.min(startDepth + this.o.getMaxTestCaseDepth(), tcFinalState.getDepth());
            final ArrayList<HashSet<String>> branchesNeighborList = new ArrayList<>();
            final ArrayList<List<State>> newStatesList = new ArrayList<>();
            final ArrayList<State> statePreFrontierList = new ArrayList<>();
            final ArrayList<Boolean> atJumpList = new ArrayList<>();
            final ArrayList<Set<String>> coveredBranchesList = new ArrayList<>();
            final ArrayList<List<String>> branchesPostFrontierList = new ArrayList<>();
            final ArrayList<List<? extends Map<Long, String>>> stringLiteralsList = new ArrayList<>();
            final ArrayList<List<? extends Set<Long>>> stringOthersList = new ArrayList<>();
            for (int currentDepth = startDepth; currentDepth < Math.min(this.o.getMaxDepth(), tcFinalDepth); ++currentDepth) {
                final List<State> newStates = rp.runProgram(currentDepth);

                //checks shutdown of the performer
                if (Thread.interrupted()) {
                    return;
                }
                
                //gives some feedback if detects a contradiction
                if (newStates.isEmpty()) {
                    LOGGER.info("Test case %s, detected contradiction while generating path conditions at depth %d", tc.getClassName(), currentDepth);
                }

                //updates neigborFrontierBranchesList
                final List<String> branchesPostFrontier = rp.getBranchesPostFrontier();
                final HashSet<String> branchesNeighbor = new HashSet<>(branchesPostFrontier);
                for (int i = 0; i < branchesNeighborList.size(); ++i) {
                	branchesNeighbor.addAll(branchesNeighborList.get(i));
                }
                branchesNeighborList.add(branchesNeighbor);                

                //updates all the other lists
                newStatesList.add(newStates);
                statePreFrontierList.add(rp.getStatePreFrontier());
                atJumpList.add(rp.getAtJump());
                coveredBranchesList.add(rp.getCoverage());
                branchesPostFrontierList.add(branchesPostFrontier);
                stringLiteralsList.add(rp.getStringLiterals());
                stringOthersList.add(rp.getStringOthers());
            }

            //puts all the output jobs in the output queue 
            boolean noOutputJobGenerated = true;
            for (int currentDepth = startDepth; currentDepth < Math.min(this.o.getMaxDepth(), tcFinalDepth); ++currentDepth) {
                final int depthLevel = currentDepth - startDepth;
                final List<State> newStates = newStatesList.get(depthLevel);

                //checks shutdown of the performer
                if (Thread.interrupted()) {
                    return;
                }

                //creates all the output jobs
                final State preState = statePreFrontierList.get(depthLevel);
                final List<String> targetBranches = branchesPostFrontierList.get(depthLevel); 
                for (int i = 0; i < newStates.size(); ++i) {
                    final State newState = newStates.get(i);
                    final List<Clause> pathCondition = newState.getPathCondition();
                    if (this.treePath.containsPath(pathCondition, false)) {
                        continue;
                    }
                    final ClassHierarchy hier = newState.getClassHierarchy();
                    if (!pathCondition.isEmpty() && assumptionViolated(hier, pathCondition.get(pathCondition.size() - 1))) {
                        LOGGER.info("From test case %s skipping path condition due to violated assumption %s on initialMap in path condition %s", tc.getClassName(), pathCondition.get(pathCondition.size() - 1), stringifyPathCondition(shorten(pathCondition)));
                        continue;
                    }
                    
                    //inserts the generated path in the treePath
                    final Map<Long, String> stringLiterals = stringLiteralsList.get(depthLevel).get(i);
                    final Set<Long> stringOthers = stringOthersList.get(depthLevel).get(i); 
                    this.treePath.insertPath(pathCondition, coveredBranchesList.get(depthLevel), branchesNeighborList.get(depthLevel), false);

                    //emits the output job in the output buffer
                    final boolean atJump = atJumpList.get(depthLevel);
                    final JBSEResult output = new JBSEResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), initialState, preState, newState, atJump, (atJump ? targetBranches.get(i) : null), stringLiterals, stringOthers, currentDepth);
                    this.getOutputBuffer().add(output);
                    LOGGER.info("From test case %s generated path condition %s%s", tc.getClassName(), stringifyPathCondition(shorten(pathCondition)), (atJump ? (" aimed at branch " + targetBranches.get(i)) : ""));
                    noOutputJobGenerated = false;
                }
            }

            if (noOutputJobGenerated) {
                LOGGER.info("From test case %s no path condition generated", tc.getClassName());
            }
        } catch (NoTargetHitException e) {
            //prints some feedback
            LOGGER.warn("Run test case %s, does not reach the target method %s:%s:%s", item.getTestCase().getClassName(), item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName());
        }
    }
}

