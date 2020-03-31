package tardis.implementation;

import static tardis.implementation.Util.shorten;
import static tardis.implementation.Util.stringifyPathCondition;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import tardis.Options;
import tardis.framework.InputBuffer;
import tardis.framework.OutputBuffer;
import tardis.framework.Performer;

public final class PerformerJBSE extends Performer<EvosuiteResult, JBSEResult> {
    private final Options o;
    private final int maxDepth;
    private final CoverageSet coverageSet;
    private final TreePath treePath = new TreePath();
    private final HashMap<String, State> initialStateCache = new HashMap<>();

    public PerformerJBSE(Options o, InputBuffer<EvosuiteResult> in, OutputBuffer<JBSEResult> out, CoverageSet coverageSet) {
        super(in, out, o.getNumOfThreads(), 1, o.getThrottleFactorJBSE(), o.getGlobalTimeBudgetDuration(), o.getGlobalTimeBudgetUnit());
        this.o = o.clone();
        this.maxDepth = o.getMaxDepth();
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
                System.out.println("[JBSE    ] Unexpected exception raised while exploring test case " + item.getTestCase().getClassName() + ": " + e.getMessage());
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
        if (this.maxDepth <= 0) {
            return;
        }
        //runs the test case up to the final state, and takes the final state's path condition
        try (final RunnerPath rp = new RunnerPath(this.o, item, possiblyGetInitialStateCached(item))) {
            final State tcFinalState = rp.runProgram();
            final State initialState = rp.getInitialState();
            possiblySetInitialStateCached(item, initialState);
            this.coverageSet.addAll(rp.getCoverage());
            final Collection<Clause> tcFinalPC = tcFinalState.getPathCondition();
            this.treePath.insertPath(tcFinalPC);

            //prints some feedback
            final TestCase tc = item.getTestCase();
            System.out.println("[JBSE    ] Run test case " + tc.getClassName() + ", path condition " + stringifyPathCondition(shorten(tcFinalPC)));
            final int coverage = this.coverageSet.size();
            System.out.println("[JBSE    ] Current coverage: " + coverage + " branch" + (coverage == 1 ? "" : "es"));
            final int tcFinalDepth = tcFinalState.getDepth();

            //reruns the test case, and generates all the modified path conditions
            boolean noPathConditionGenerated = true;
            for (int currentDepth = startDepth; currentDepth < Math.min(this.maxDepth, tcFinalDepth); ++currentDepth) {
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
                    if (this.treePath.containsPath(currentPC)) {
                        continue;
                    }
                    final JBSEResult output = new JBSEResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), initialState, preState, newState, atJump, (atJump ? targetBranches.get(i) : null), stringLiterals, currentDepth);
                    this.getOutputBuffer().add(output);
                    this.treePath.insertPath(currentPC);
                    System.out.println("[JBSE    ] From test case " + tc.getClassName() + " generated path condition " + stringifyPathCondition(shorten(currentPC)) + (atJump ? (" aimed at branch " + targetBranches.get(i)) : ""));
                    noPathConditionGenerated = false;
                }
            }
            if (noPathConditionGenerated) {
                System.out.println("[JBSE    ] From test case " + tc.getClassName() + " no path condition generated");
            }
        }
    }
}

