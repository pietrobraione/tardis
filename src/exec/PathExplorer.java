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

	private final RunnerPath rp;
	private final PathConditionHandler handlerPC;

	static private String indent = "";
	static private int testcount = 0;

	public PathExplorer(RunnerPath runner){
		this.rp = new RunnerPath();
		this.handlerPC = new PathConditionHandler(this.rp);
	}


	/**
	 * Executes a test case and generates tests for all the alternative branches
	 * starting from some depth up to some maximum depth.
	 * 
	 * @param tc a {@link TestCase}.
	 * @param startDepth the depth to which generation of tests must be started.
	 * @param maxDepth the maximum depth at which generation of tests is performed.
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
	public void explore(TestCase tc, int startDepth, int maxDepth) 
			throws DecisionException, CannotBuildEngineException, InitializationException, 
			InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
			ClasspathException, CannotBacktrackException, CannotManageStateException, 
			ThreadStackEmptyException, ContradictionException, EngineStuckException, 
			FailureException {
		if (maxDepth <= 0) {
			return;
		}

		//runs the test case up to the final state, and takes the final state's path condition
		final State tcFinalState = rp.runProgram(tc, -1).get(0);
		final Collection<Clause> tcFinalPC = tcFinalState.getPathCondition();
		final int tcFinalDepth = tcFinalState.getDepth();

		System.out.println(indent + "Test_" + (++testcount) + ": Executed PC= " + tcFinalPC); 

		for (int currentDepth = startDepth; currentDepth < Math.min(maxDepth, tcFinalDepth - 1); currentDepth++) {
			System.out.println(indent + "POS=" + currentDepth);

			final List<State> newStates = rp.runProgram(tc, currentDepth);
			int currentBreadth = 0;
			for (State newState : newStates) {
				final Collection<Clause> currentPC = newState.getPathCondition();
				if (alreadyExplored(currentPC, tcFinalPC)) {
					continue;
				}

				System.out.println(indent + "** currently considered PC: " + currentPC);
				System.out.print(indent);
				String indentBak = indent;
				indent += "  ";
				
				handlerPC.generateTestCases(newState, currentDepth, currentBreadth, maxDepth);
				
				indent = indentBak;
				System.out.println(indent + "BACK"); 
				
				currentBreadth++;
			}

		}

		System.out.println(indent + "DONE"); 
	}

	private boolean alreadyExplored(Collection<Clause> newPC, Collection<Clause> oldPC){
		Collection<Clause> donePC = Arrays.asList(Arrays.copyOfRange(
				oldPC.toArray(new Clause[0]), 0, 
				newPC.size()));
		if (donePC.toString().equals(newPC.toString())){
			return true;
		}else{
			return false;
		}
	}






}

