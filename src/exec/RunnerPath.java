package exec;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;


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
import sushi.execution.jbse.StateFormatterSushiPathCondition;

public class RunnerPath {
	private State initialState;
	private DecisionProcedureGuidance dp;
	private String className;
	private String parametersSignature;
	private String methodName;
	private String z3Path;
	private String tmpPath;
	private String[] classpath;
	
	public RunnerPath(Options o) {
		this.className = o.getGuidedMethod().get(0);
		this.parametersSignature = o.getGuidedMethod().get(1);
		this.methodName = o.getGuidedMethod().get(2);
		this.classpath = new String[3];
		this.classpath[0] = o.getBinPath().toString();
		this.classpath[1] = o.getJBSELibraryPath().toString();
		this.classpath[2] = o.getJREPath().toString();
		this.z3Path = o.getZ3Path().toString();
		this.tmpPath = o.getTmpDirectoryBase().toString();
	}

	private RunnerParameters commonParams = null;
	private RunnerParameters commonGuidance = null;

	private  RunnerParameters getCommonParams() throws DecisionException {
		if (commonParams == null) {
			//builds the parameters for the guided (symbolic) execution
			RunnerParameters p = new RunnerParameters();

			//the guided method (to be executed symbolically)
			String subjectClassName = className;
			String subjectParametersSignature = parametersSignature;
			String subjectMethodName = methodName;

			CalculatorRewriting calc = new CalculatorRewriting();
			calc.addRewriter(new RewriterOperationOnSimplex());


			p.addClasspath(classpath);
			p.setMethodSignature(subjectClassName, subjectParametersSignature, subjectMethodName);
			p.setCalculator(calc);
			DecisionProcedureAlgorithms pd = new DecisionProcedureAlgorithms(
					new DecisionProcedureClassInit( //useless?
							new DecisionProcedureLICS( //useless?
									new DecisionProcedureSMTLIB2_AUFNIRA(
											new DecisionProcedureAlwSat(), 
											calc, z3Path + " -smt2 -in -t:10"), 
									calc, new LICSRulesRepo()), 
							calc, new ClassInitRulesRepo()), calc);
			p.setDecisionProcedure(pd);
			p.setBreadthMode(BreadthMode.ALL_DECISIONS_NONTRIVIAL);

			commonParams = p;

		}
		return commonParams;
	}

	private RunnerParameters getCommonGuidance(RunnerParameters p) throws DecisionException {
		if (commonGuidance == null) {

			//builds the parameters for the guiding (concrete) execution
			RunnerParameters pGuidance = new RunnerParameters();
			pGuidance.addClasspath(classpath);

			pGuidance.setCalculator(p.getCalculator());
			pGuidance.setDecisionProcedure(
					new DecisionProcedureAlgorithms(
							new DecisionProcedureClassInit(
									new DecisionProcedureAlwSat(), 
									(CalculatorRewriting) p.getCalculator(), new ClassInitRulesRepo()), 
							p.getCalculator())); //for concrete execution
			pGuidance.setStateIdentificationMode(StateIdentificationMode.COMPACT);
			pGuidance.setBreadthMode(BreadthMode.ALL_DECISIONS);



			commonGuidance = pGuidance;
		}



		return commonGuidance;
	}



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
		final RunnerParameters p = getCommonParams();

		//builds the parameters for the guiding (concrete) execution
		final RunnerParameters pGuidance = getCommonGuidance(p);

		//the guiding method (to be executed concretely)
		final String testClassName = testCase.getClassName();
		final String testParametersSignature = testCase.getParameterSignature();
		final String testMethodName = testCase.getMethodName();
		pGuidance.setMethodSignature(testClassName, testParametersSignature, testMethodName);

		//the guidance decision procedure
		final DecisionProcedureGuidance guid = new DecisionProcedureGuidance(p.getDecisionProcedure(),
				p.getCalculator(), pGuidance, p.getMethodSignature());
		p.setDecisionProcedure(guid);

		//the return value
		final List<State> stateList = new ArrayList<State>();

		p.setActions(new Actions() {
			private int currentDepth = 0;
			
			//this MUST be present, or the guiding execution will not advance!!!!
			@Override
			public boolean atStepPre() {
				try {
					if (this.getEngine().getCurrentState().getCurrentMethodSignature().equals( 
							guid.getCurrentMethodSignature())) {
						guid.step();
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
				if (testDepth == 0) {
					guid.endGuidance();
				}
				return super.atRoot();
			}

			@Override
			public boolean atBranch(BranchPoint bp) {
				this.currentDepth++;				
				if (this.currentDepth == testDepth) {
					guid.endGuidance();
				} else if (this.currentDepth == testDepth + 1) {
					stateList.add(this.getEngine().getCurrentState().clone());
					this.getEngine().stopCurrentTrace();
				}

				return super.atBranch(bp);
			}

			@Override
			public boolean atBacktrackPost(BranchPoint bp) {
				stateList.add(this.getEngine().getCurrentState().clone());
				this.getEngine().stopCurrentTrace();

				return super.atBacktrackPost(bp);
			}

			@Override
			public void atEnd() {
				if (testDepth < 0) {
					final State finalState = this.getEngine().getCurrentState();
					stateList.add(finalState);
				}
				super.atEnd();
			}
		});

		//builds the runner
		final RunnerBuilder rb = new RunnerBuilder();
		final Runner r = rb.build(p);
		r.run();

		this.initialState = rb.getEngine().getInitialState();
		this.dp = guid;

		return stateList;

	}

	/**
	 * Must be invoked after an invocation of {@link #runProgram(TestCase, int)}.
	 * Generates the EvoSuite wrapper for the path condition of some state.
	 * 
	 * @param testCount a {@link TestIdentifier} (used only to disambiguate
	 *        file names).
	 * @param state a {@link State}; must be some state in the execution 
	 *        triggered by {@link #runProgram(TestCase, int)}.
	 * @return a {@link String}, the file name of the generated EvoSuite wrapper.
	 */
	public String emitEvoSuiteWrapper(TestIdentifier testCount, State state) {
		final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount.getTestCount(), () -> this.initialState, () -> {
			try {
				return dp.getModel();
			} catch (DecisionException e1) {
				return null;
			}
		});
		fmt.formatPrologue();
		fmt.formatState(state);
		fmt.formatEpilogue();

		final String fileName = tmpPath + "/EvoSuiteWrapper_" + testCount.getTestCount() +".java";
		try (final BufferedWriter w = Files.newBufferedWriter(Paths.get(fileName))) {
			w.write(fmt.emit());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		fmt.cleanup();

		return fileName;
	}
}
