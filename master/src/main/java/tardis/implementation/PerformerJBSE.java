package tardis.implementation;

import static jbse.apps.run.JAVA_MAP_Utils.assumptionViolated;
import static tardis.implementation.Util.shorten;
import static tardis.implementation.Util.stringifyPathCondition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
            //runs the test case up to the final state, and takes the initial state, 
            //the coverage set, and the final state's path condition
            final State tcFinalState = rp.runProgram();
            final Collection<Clause> tcFinalPC = tcFinalState.getPathCondition();

            //prints some feedback
            final TestCase tc = item.getTestCase();
            LOGGER.info("Run test case %s, path condition %s", tc.getClassName(), stringifyPathCondition(shorten(tcFinalPC)));
            
            //skips the test case if its path was already covered,
            //otherwise records its path
            final Set<String> newCoveredBranches;
            synchronized (this.treePath) {
                if (this.treePath.containsPath(tcFinalPC, true)) {
                    LOGGER.info("Test case %s redundant, skipped", tc.getClassName());
                    return;
                }
                newCoveredBranches = this.treePath.insertPath(tcFinalPC, rp.getCoverage(), Collections.emptySet(), true);
            }

            //Check if test case is generated by the Evosuite seed process (startDepth == -1)
            if(startDepth == 0) {
            	this.treePath.PCToBloomFilterEvosuiteSeed(tcFinalPC);
            }
            
            //possibly caches the initial state
            final State initialState = rp.getInitialState();
            possiblySetInitialStateCached(item, initialState);
            
            //records coverage
            final Set<String> newCoveredBranchesTarget = filterBranchesTarget(newCoveredBranches);            
            final Set<String> newCoveredBranchesUnsafe = filterBranchesUnsafe(newCoveredBranches);
            
            //recalculates indices of items in output queue
            if (newCoveredBranchesTarget.size() > 0) {
                this.out.updateImprovabilityIndex(newCoveredBranchesTarget);
            }
            //TODO this.out.updateNoveltyIndex(newCoveredBranches? newCoveredBranchesTarget?), nel caso in cui tale insieme abbia dimensione > 0;

            //produces feedback and emits the test
            final Coverage coverage = this.o.getCoverage();
            if (coverage == Coverage.PATHS || 
            (coverage == Coverage.BRANCHES && newCoveredBranchesTarget.size() > 0) ||
            (coverage == Coverage.UNSAFE && newCoveredBranchesUnsafe.size() > 0)) {
                //more feedback
                if (coverage == Coverage.BRANCHES) {
                    LOGGER.info("Test case %s covered branch%s %s", tc.getClassName(), (newCoveredBranchesTarget.size() == 1 ? " " : "es"), String.join(", ", newCoveredBranches));
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
            final long pathCoverage = this.pathCoverage.incrementAndGet();
            final int branchCoverage = this.treePath.totalCovered();
            final int branchCoverageTarget = this.treePath.totalCovered(patternBranchesTarget());
            final int branchCoverageUnsafe = this.treePath.totalCovered(patternBranchesUnsafe());
            LOGGER.info("Current coverage: %d path%s, %d branch%s (total), %d branch%s (target), %d failed assertion%s", pathCoverage, (pathCoverage == 1 ? "" : "s"), branchCoverage, (branchCoverage == 1 ? "" : "es"), branchCoverageTarget, (branchCoverageTarget == 1 ? "" : "es"), branchCoverageUnsafe, (branchCoverageUnsafe == 1 ? "" : "s"));
            
            //reruns the test case at all the depths in the range generates all the modified path conditions
            //and gathers the necessary information
            final int tcFinalDepth = Math.min(startDepth + this.o.getMaxTestCaseDepth(), tcFinalState.getDepth());
            boolean noPathConditionGenerated = true;
            final ArrayList<HashSet<String>> neighborFrontierBranchesList = new ArrayList<>();
            final ArrayList<List<State>> newStateLists = new ArrayList<>();
            final ArrayList<State> preStateList = new ArrayList<>();
            final ArrayList<Boolean> atJumpList = new ArrayList<>();
            final ArrayList<HashSet<String>> coverageBranchesLists = new ArrayList<>();
            final ArrayList<List<String>> targetBranchesLists = new ArrayList<>();
            final ArrayList<List<? extends Map<Long, String>>> stringLiteralsLists = new ArrayList<>();
            final ArrayList<List<? extends Set<Long>>> stringOthersLists = new ArrayList<>();
            for (int currentDepth = startDepth; currentDepth < Math.min(this.o.getMaxDepth(), tcFinalDepth); ++currentDepth) {
                final List<State> newStates = rp.runProgram(currentDepth);

                //checks shutdown of the performer
                if (Thread.interrupted()) {
                    return;
                }

                //selects post-frontier branches in the target class or the target method
                final List<String> branchesPostFrontier = rp.getBranchesPostFrontier();
                final Set<String> branchesPostFrontierTarget = filterBranchesTarget(new HashSet<>(branchesPostFrontier));
                
                //selects all the post-frontier target branches that are not 
                //yet covered
                final HashSet<String> branchesPostFrontierTargetNotCovered = new HashSet<>();
                for (String branch : branchesPostFrontierTarget) {
                    if (!this.treePath.covers(branch)) {
                        branchesPostFrontierTargetNotCovered.add(branch);
                    }
                }
                
                //updates neigborFrontierBranchesList
                if (branchesPostFrontierTargetNotCovered.size() > 0) {
                    //updates all the previous lists into improvabilityIndexLists
                    for (int i = 0; i < neighborFrontierBranchesList.size(); ++i) {
                        neighborFrontierBranchesList.get(i).addAll(branchesPostFrontierTargetNotCovered);
                    }
                }
                neighborFrontierBranchesList.add(branchesPostFrontierTargetNotCovered);
                                
                //updates all the other lists
                newStateLists.add(newStates);
                preStateList.add(rp.getPreState());
                atJumpList.add(rp.getAtJump());
                coverageBranchesLists.add(rp.getCoverage());
                targetBranchesLists.add(branchesPostFrontier);
                stringLiteralsLists.add(rp.getStringLiterals());
                stringOthersLists.add(rp.getStringOthers());
            }

            //puts all the output jobs in the output queue 
            for (int currentDepth = startDepth; currentDepth < Math.min(this.o.getMaxDepth(), tcFinalDepth); ++currentDepth) {
                final int depthLevel = currentDepth - startDepth;
                final List<State> newStates = newStateLists.get(depthLevel);

                //checks shutdown of the performer
                if (Thread.interrupted()) {
                    return;
                }

                //creates all the output jobs
                final State preState = preStateList.get(depthLevel);
                final boolean atJump = atJumpList.get(depthLevel);
                final List<String> targetBranches = targetBranchesLists.get(depthLevel); 
                for (int i = 0; i < newStates.size(); ++i) {
                    final State newState = newStates.get(i);
                    final Map<Long, String> stringLiterals = stringLiteralsLists.get(depthLevel).get(i);
                    final Set<Long> stringOthers = stringOthersLists.get(depthLevel).get(i); 
                    final List<Clause> currentPC = newState.getPathCondition();
                    final ClassHierarchy hier = newState.getClassHierarchy();
                    if (this.treePath.containsPath(currentPC, false)) {
                        continue;
                    }
                    if (!currentPC.isEmpty() && assumptionViolated(hier, currentPC.get(currentPC.size() - 1))) {
                        LOGGER.info("From test case %s skipping path condition due to violated assumption %s on initialMap in path condition %s", tc.getClassName(), currentPC.get(currentPC.size() - 1), stringifyPathCondition(shorten(currentPC)));
                        continue;
                    }

                    if (atJump) {
                        final HashSet<String> totallyCoveredBranches = new HashSet<>();
                        totallyCoveredBranches.addAll(coverageBranchesLists.get(depthLevel));
                        totallyCoveredBranches.add(targetBranches.get(i));
                        this.treePath.insertPath(currentPC, totallyCoveredBranches, neighborFrontierBranchesList.get(depthLevel), false);
                    } else {
                        this.treePath.insertPath(currentPC, coverageBranchesLists.get(depthLevel), neighborFrontierBranchesList.get(depthLevel), false);
                    }

                    final JBSEResult output = new JBSEResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), initialState, preState, newState, atJump, (atJump ? targetBranches.get(i) : null), stringLiterals, stringOthers, currentDepth);
                    this.getOutputBuffer().add(output);
                    LOGGER.info("From test case %s generated path condition %s%s", tc.getClassName(), stringifyPathCondition(shorten(currentPC)), (atJump ? (" aimed at branch " + targetBranches.get(i)) : ""));
                    noPathConditionGenerated = false;
                }
            }

            if (noPathConditionGenerated) {
                LOGGER.info("From test case %s no path condition generated", tc.getClassName());
            }
        } catch (NoTargetHitException e) {
            //prints some feedback
            LOGGER.warn("Run test case %s, does not reach the target method %s:%s:%s", item.getTestCase().getClassName(), item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName());
        }
    }
    
    private static String toPattern(String s) {
        return s
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("$", "\\$");
    }
    
    private String patternBranchesTarget() {
        return (this.o.getTargetClass() == null) ? (this.o.getTargetMethod().get(0) + ":" + toPattern(this.o.getTargetMethod().get(1)) + ":" + this.o.getTargetMethod().get(2) + ":.*:.*") : (toPattern(this.o.getTargetClass()) + "(\\$.*)*:.*:.*:.*:.*");
    }
    
    private Set<String> filterBranchesTarget(Set<String> newCoveredBranches) {
        final Pattern p = Pattern.compile(patternBranchesTarget()); 
        final Set<String> retVal = newCoveredBranches.stream().filter(s -> { final Matcher m = p.matcher(s); return m.matches(); }).collect(Collectors.toSet());
        return retVal;
    }
    
    private String patternBranchesUnsafe() {
        return "jbse/meta/Analysis:\\(Z\\)V:ass3rt:1:4"; //TODO find a more robust way
    }
    
    private Set<String> filterBranchesUnsafe(Set<String> newCoveredBranches) {
        final Pattern p = Pattern.compile(patternBranchesUnsafe()); 
        final Set<String> retVal = newCoveredBranches.stream().filter(s -> { final Matcher m = p.matcher(s); return m.matches(); }).collect(Collectors.toSet());
        return retVal;
    }
}

