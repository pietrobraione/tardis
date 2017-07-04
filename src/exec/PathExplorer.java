package exec;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

public class PathExplorer {
	private final int maxDepth;
	private final RunnerPath rp;
	private final PathConditionHandler handlerPC;
	static private String indent = "";

	public PathExplorer(Options o) {
		this.maxDepth = o.getMaxDepth();
		this.rp = new RunnerPath(o);
		this.handlerPC = new PathConditionHandler(o);
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
	public void explore(TestIdentifier testCount, TestCase tc, int startDepth) 
			throws DecisionException, CannotBuildEngineException, InitializationException, 
			InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
			ClasspathException, CannotBacktrackException, CannotManageStateException, 
			ThreadStackEmptyException, ContradictionException, EngineStuckException, 
			FailureException {
		if (this.maxDepth <= 0) {
			return;
		}

		//runs the test case up to the final state, and takes the final state's path condition
		final State tcFinalState = this.rp.runProgram(tc, -1).get(0);
		final Collection<Clause> tcFinalPC = tcFinalState.getPathCondition();
		final int tcFinalDepth = tcFinalState.getDepth();
		testCount.testIncrease();
		System.out.println(indent + "Test_" + testCount.getTestCount() + ": Executed PC= " + tcFinalPC); 

		for (int currentDepth = startDepth; currentDepth < Math.min(this.maxDepth, tcFinalDepth - 1); currentDepth++) {
			System.out.println(indent + "DEPTH=" + currentDepth);

			final List<State> newStates = this.rp.runProgram(tc, currentDepth);
			final State initialState = this.rp.getInitialState();
			for (State newState : newStates) {
				final Collection<Clause> currentPC = newState.getPathCondition();
				if (alreadyExplored(currentPC, tcFinalPC)) {
					continue;
				}

				System.out.println(indent + "** currently considered PC: " + currentPC);
				System.out.print(indent);
				final String indentBak = indent;
				indent += "  ";

				this.handlerPC.generateTestCases(testCount, initialState, newState, currentDepth);

				indent = indentBak;
				System.out.println(indent + "BACK"); 
			}

		}

		System.out.println(indent + "DONE"); 
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
}

