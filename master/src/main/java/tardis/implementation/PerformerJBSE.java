package tardis.implementation;

import static tardis.implementation.Util.shorten;
import static tardis.implementation.Util.stringifyPathCondition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import jbse.algo.exc.CannotManageStateException;
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
import tardis.framework.OutputBuffer;
import tardis.framework.Performer;

public final class PerformerJBSE extends Performer<EvosuiteResult, JBSEResult> {
    private final Options o;
    private final CoverageSet coverageSet;
    private final TreePath treePath = new TreePath();
    private final HashMap<String, State> initialStateCache = new HashMap<>();
    private AtomicLong pathCoverage = new AtomicLong(0);

    public PerformerJBSE(Options o, InputBuffer<EvosuiteResult> in, OutputBuffer<JBSEResult> out, CoverageSet coverageSet) {
        super(in, out, o.getNumOfThreads(), 1, o.getThrottleFactorJBSE(), o.getGlobalTimeBudgetDuration(), o.getGlobalTimeBudgetUnit());
        this.o = o.clone();
        this.coverageSet = coverageSet;
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
                System.out.println("[JBSE    ] Unexpected exception raised while exploring test case " + item.getTestCase().getClassName() + ": " + e);
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
            System.out.println("[JBSE    ] Run test case " + tc.getClassName() + ", path condition " + stringifyPathCondition(shorten(tcFinalPC)));
            
            //skips the test case if its path was already covered,
            //otherwise records its path
            synchronized (this.treePath) {
                if (this.treePath.containsPath(tcFinalPC, true)) {
                    System.out.println("[JBSE    ] Test case " + tc.getClassName() + " redundant, skipped");
                    return;
                }
                this.treePath.insertPath(tcFinalPC, true);
            }
            
            //possibly caches the initial state
            final State initialState = rp.getInitialState();
            possiblySetInitialStateCached(item, initialState);
            
            //records coverage, emits tests, prints feedback
            final Set<String> newCoveredBranches = this.coverageSet.addAll(rp.getCoverage());
            final Coverage coverage = this.o.getCoverage();
            if (coverage == Coverage.PATHS || (coverage == Coverage.BRANCHES && newCoveredBranches.size() > 0)) {
                //more feedback
                if (coverage == Coverage.BRANCHES) {
                    System.out.println("[JBSE    ] Test case " + tc.getClassName() + " covered branch" + (newCoveredBranches.size() == 1 ? " " : "es ") + String.join(", ", newCoveredBranches));
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
                    System.out.println("[JBSE    ] Error while attempting to copy test case " + tc.getClassName() + " or its scaffolding to its destination directory: " + e);
                }
            }
            final long pathCoverage = this.pathCoverage.incrementAndGet();
            final int branchCoverage = this.coverageSet.size();
            System.out.println("[JBSE    ] Current coverage: " + pathCoverage + " path" + (pathCoverage == 1 ? ", " : "s, ") + branchCoverage + " branch" + (branchCoverage == 1 ? "" : "es"));

            //reruns the test case, and generates all the modified path conditions
            final int tcFinalDepth = Math.min(startDepth + this.o.getMaxTestCaseDepth(), tcFinalState.getDepth());
            boolean noPathConditionGenerated = true;
            synchronized (this.treePath) {
                for (int currentDepth = startDepth; currentDepth < Math.min(this.o.getMaxDepth(), tcFinalDepth); ++currentDepth) {
                    //runs the program
                    final List<State> newStates = rp.runProgram(currentDepth);

                    //checks shutdown of the performer
                    if (Thread.interrupted()) {
                        return;
                    }

                    //creates all the output jobs
                    final State preState = rp.getPreState();
                    final boolean atJump = rp.getAtJump();
                    final List<String> targetBranches = rp.getTargetBranches(); 
                    final Map<Long, String> stringLiterals = rp.getStringLiterals();
                    for (int i = 0; i < newStates.size(); ++i) {
                        final State newState = newStates.get(i);
                        final Collection<Clause> currentPC = newState.getPathCondition();
                        if (this.treePath.containsPath(currentPC, false)) {
                            continue;
                        }
                        final JBSEResult output = new JBSEResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), initialState, preState, newState, atJump, (atJump ? targetBranches.get(i) : null), stringLiterals, currentDepth);
                        this.getOutputBuffer().add(output);
                        this.treePath.insertPath(currentPC, false);
                        System.out.println("[JBSE    ] From test case " + tc.getClassName() + " generated path condition " + stringifyPathCondition(shorten(currentPC)) + (atJump ? (" aimed at branch " + targetBranches.get(i)) : ""));
                        noPathConditionGenerated = false;
                    }
                }
            }
            if (noPathConditionGenerated) {
                System.out.println("[JBSE    ] From test case " + tc.getClassName() + " no path condition generated");
            }
        } catch (NoTargetHitException e) {
            //prints some feedback
            System.out.println("[JBSE    ] Run test case " + item.getTestCase().getClassName() + ", does not reach the target method " + item.getTargetMethodClassName() + ":" + item.getTargetMethodDescriptor() + ":" + item.getTargetMethodName());
        }
    }
}

