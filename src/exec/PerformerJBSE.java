package exec;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import concurrent.Performer;
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

public class PerformerJBSE extends Performer<EvosuiteResult, JBSEResult> {
	private final Options o;
	private final int maxDepth;

	public PerformerJBSE(Options o, LinkedBlockingQueue<EvosuiteResult> in, LinkedBlockingQueue<JBSEResult> out) {
		super(in, out, o.getNumOfThreads());
		this.o = o.clone();
		this.maxDepth = o.getMaxDepth();
	}

	/**
	 * Executes a test case and generates tests for all the alternative branches
	 * starting from some depth up to some maximum depth.
	 * 
	 * @param tc a {@link TestCase}.
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
	public void explore(TestCase tc, int startDepth) 
			throws DecisionException, CannotBuildEngineException, InitializationException, 
			InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
			ClasspathException, CannotBacktrackException, CannotManageStateException, 
			ThreadStackEmptyException, ContradictionException, EngineStuckException, 
			FailureException {
		if (this.maxDepth <= 0) {
			return;
		}
		//runs the test case up to the final state, and takes the final state's path condition
		final RunnerPath rp = new RunnerPath(this.o);
		final State tcFinalState = rp.runProgram(tc, -1).get(0);
		final Collection<Clause> tcFinalPC = tcFinalState.getPathCondition();
		System.out.println("Run test case " + tc.getClassName() +", path condition " + tcFinalPC.toString());
		final int tcFinalDepth = tcFinalState.getDepth();
		for (int currentDepth = startDepth; currentDepth < Math.min(this.maxDepth, tcFinalDepth - 1); currentDepth++) {
			final List<State> newStates = rp.runProgram(tc, currentDepth);
			final State initialState = rp.getInitialState();
			for (State newState : newStates) {
				final Collection<Clause> currentPC = newState.getPathCondition();
				if (alreadyExplored(currentPC, tcFinalPC)) {
					continue;
				}
				this.getOutputQueue().add(new JBSEResult(initialState, newState, currentDepth));
			}
		}
	}

	private static boolean alreadyExplored(Collection<Clause> newPC, Collection<Clause> oldPC) {
		final List<Clause> donePC = Arrays.asList(Arrays.copyOfRange(
				oldPC.toArray(new Clause[0]), 0, 
				newPC.size()));
		if (donePC.toString().equals(newPC.toString())) {
			return true;
		} else {
			return false;
		}
	}

	
	@Override
	protected Runnable makeJob(EvosuiteResult item) {
		final Runnable job = () -> {
			try {
				explore(item.getTestCase(), item.getStartDepth());
			} catch (DecisionException | CannotBuildEngineException | InitializationException |
					InvalidClassFileFactoryClassException | NonexistingObservedVariablesException |
					ClasspathException | CannotBacktrackException | CannotManageStateException |
					ThreadStackEmptyException | ContradictionException | EngineStuckException |
					FailureException e ) {

				e.printStackTrace();
			} 
		};
		return job;
	}
	
}

