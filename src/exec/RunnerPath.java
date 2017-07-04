package exec;

import java.util.ArrayList;
import java.util.List;


import jbse.algo.exc.CannotManageStateException;
import jbse.apps.run.DecisionProcedureGuidance;
import jbse.apps.run.GuidanceException;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.common.exc.ClasspathException;
import jbse.dec.DecisionProcedureAlgorithms;
import jbse.dec.DecisionProcedureAlwSat;
import jbse.dec.DecisionProcedureClassInit;
import jbse.dec.DecisionProcedureLICS;
import jbse.dec.DecisionProcedureSMTLIB2_AUFNIRA;
import jbse.dec.exc.DecisionException;
import jbse.jvm.Runner;
import jbse.jvm.RunnerBuilder;
import jbse.jvm.RunnerParameters;
import jbse.jvm.EngineParameters.BreadthMode;
import jbse.jvm.EngineParameters.StateIdentificationMode;
import jbse.jvm.Runner.Actions;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.rewr.CalculatorRewriting;
import jbse.rewr.RewriterOperationOnSimplex;
import jbse.rules.ClassInitRulesRepo;
import jbse.rules.LICSRulesRepo;
import jbse.tree.StateTree.BranchPoint;

public class RunnerPath {
	private final String[] classpath;
	private final String z3Path;
	private final RunnerParameters commonParams;
	private final RunnerParameters commonGuidance;
		
	public RunnerPath(Options o) {
		this.classpath = new String[3];
		this.classpath[0] = o.getBinPath().toString();
		this.classpath[1] = o.getJBSELibraryPath().toString();
		this.classpath[2] = o.getJREPath().toString();
		this.z3Path = o.getZ3Path().toString();
		
		//builds the template parameters object for the guided (symbolic) execution
		this.commonParams = new RunnerParameters();
		this.commonParams.setMethodSignature(o.getGuidedMethod().get(0), o.getGuidedMethod().get(1), o.getGuidedMethod().get(2));
		this.commonParams.addClasspath(this.classpath);
		this.commonParams.setBreadthMode(BreadthMode.ALL_DECISIONS_NONTRIVIAL);
		
		//builds the template parameters object for the guiding (concrete) execution
		this.commonGuidance = new RunnerParameters();
		this.commonGuidance.addClasspath(this.classpath);
		this.commonGuidance.setStateIdentificationMode(StateIdentificationMode.COMPACT);
		this.commonGuidance.setBreadthMode(BreadthMode.ALL_DECISIONS);
	}

	private static class ActionsRunner extends Actions {
		private final int testDepth;
		private final DecisionProcedureGuidance guid;
		private int currentDepth = 0;
		private final ArrayList<State> stateList = new ArrayList<State>();
		
		public ActionsRunner(int testDepth, DecisionProcedureGuidance guid) {
			this.testDepth = testDepth;
			this.guid = guid;
		}
		
		public ArrayList<State> getStateList() {
			return this.stateList;
		}
		
		//this MUST be present, or the guiding execution will not advance!!!!
		@Override
		public boolean atStepPre() {
			try {
				if (this.getEngine().getCurrentState().getCurrentMethodSignature().equals( 
						this.guid.getCurrentMethodSignature())) {
					this.guid.step();
				}
			} catch (GuidanceException | CannotManageStateException | ThreadStackEmptyException e) {
				e.printStackTrace();
				return true;
			}
			//put here your stuff, if you want to do something							

			return super.atStepPre();
		}

		@Override
		public boolean atRoot() {
			if (this.testDepth == 0) {
				this.guid.endGuidance();
			}
			return super.atRoot();
		}

		@Override
		public boolean atBranch(BranchPoint bp) {
			this.currentDepth++;				
			if (this.currentDepth == this.testDepth) {
				this.guid.endGuidance();
			} else if (this.currentDepth == this.testDepth + 1) {
				this.stateList.add(this.getEngine().getCurrentState().clone());
				this.getEngine().stopCurrentTrace();
			}

			return super.atBranch(bp);
		}

		@Override
		public boolean atBacktrackPost(BranchPoint bp) {
			this.stateList.add(this.getEngine().getCurrentState().clone());
			this.getEngine().stopCurrentTrace();

			return super.atBacktrackPost(bp);
		}

		@Override
		public void atEnd() {
			if (this.testDepth < 0) {
				final State finalState = this.getEngine().getCurrentState();
				this.stateList.add(finalState);
			}
			super.atEnd();
		}
	}

	private State initialState;
	
	/**
	 * Does symbolic execution guided by a test case up to some depth, 
	 * then peeks the states on the next branch.  
	 * 
	 * @param testCase a {@link TestCase}, it will guide symbolic execution.
	 * @param testDepth the maximum depth up to which {@code t} guides 
	 *        symbolic execution, or a negative value.
	 * @return a {@link List}{@code <}{@link State}{@code >} containing
	 *         all the states at depth {@code stateDepth + 1}. In case 
	 *         {@code stateDepth < 0} executes the test up to the final
	 *         state and returns a list containing only the final state.
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
	public List<State> runProgram(TestCase testCase, int testDepth)
			throws DecisionException, CannotBuildEngineException, InitializationException, 
			InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
			ClasspathException, CannotBacktrackException, CannotManageStateException, 
			ThreadStackEmptyException, ContradictionException, EngineStuckException, 
			FailureException {

		//builds the parameters for the guided (symbolic) execution
		final RunnerParameters p = this.commonParams.clone();
		final RunnerParameters pGuidance = this.commonGuidance.clone();


		//the calculator
		final CalculatorRewriting calc = new CalculatorRewriting();
		calc.addRewriter(new RewriterOperationOnSimplex());
		p.setCalculator(calc);
		pGuidance.setCalculator(calc);
		
		//the decision procedure
		p.setDecisionProcedure(new DecisionProcedureAlgorithms(
				new DecisionProcedureClassInit( //useless?
						new DecisionProcedureLICS( //useless?
								new DecisionProcedureSMTLIB2_AUFNIRA(
										new DecisionProcedureAlwSat(), 
										calc, this.z3Path + " -smt2 -in -t:10"), 
								calc, new LICSRulesRepo()), 
						calc, new ClassInitRulesRepo()), calc));

		//the decision procedure
		pGuidance.setDecisionProcedure(
				new DecisionProcedureAlgorithms(
						new DecisionProcedureClassInit(
								new DecisionProcedureAlwSat(), 
								calc, new ClassInitRulesRepo()), 
						calc)); //for concrete execution

		//the guiding method (to be executed concretely)
		pGuidance.setMethodSignature(testCase.getClassName(), testCase.getParameterSignature(), testCase.getMethodName());

		//the guidance decision procedure
		final DecisionProcedureGuidance guid = new DecisionProcedureGuidance(p.getDecisionProcedure(),
				p.getCalculator(), pGuidance, p.getMethodSignature());
		p.setDecisionProcedure(guid);
		
		final ActionsRunner actions = new ActionsRunner(testDepth, guid);
		p.setActions(actions);

		//builds the runner
		final RunnerBuilder rb = new RunnerBuilder();
		final Runner r = rb.build(p);
		r.run();

		this.initialState = rb.getEngine().getInitialState();

		return actions.getStateList();

	}
	
	/**
	 * Must be invoked after an invocation of {@link #runProgram(TestCase, int)}.
	 * Returns the initial state of symbolic execution.
	 * 
	 * @return a {@link State}.
	 */
	public State getInitialState() {
		return this.initialState;
	}
}
