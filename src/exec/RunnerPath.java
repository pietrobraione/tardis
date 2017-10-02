package exec;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import jbse.algo.exc.CannotManageStateException;
import jbse.apps.run.DecisionProcedureGuidance;
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
	private static final String COMMANDLINE_LAUNCH_Z3 = System.getProperty("os.name").toLowerCase().contains("windows") ? " /smt2 /in /t:10" : " -smt2 -in -t:10";

	private final String[] classpath;
	private final String z3Path;
	private final String outPath;
	private final String targetMethod;
	private final RunnerParameters commonParamsGuided;
	private final RunnerParameters commonParamsGuiding;
		
	public RunnerPath(Options o) {
		this.classpath = new String[3];
		this.classpath[0] = o.getBinPath().toString();
		this.classpath[1] = o.getJBSELibraryPath().toString();
		this.classpath[2] = o.getJREPath().toString();
		this.z3Path = o.getZ3Path().toString();
		this.outPath = o.getOutDirectory().toString();
		this.targetMethod = o.getTargetMethod().get(2);
		
		//builds the template parameters object for the guided (symbolic) execution
		this.commonParamsGuided = new RunnerParameters();
		this.commonParamsGuided.setMethodSignature(o.getTargetMethod().get(0), o.getTargetMethod().get(1), o.getTargetMethod().get(2));
		this.commonParamsGuided.addClasspath(this.classpath);
		this.commonParamsGuided.setBreadthMode(BreadthMode.ALL_DECISIONS_NONTRIVIAL);
		
		//builds the template parameters object for the guiding (concrete) execution
		this.commonParamsGuiding = new RunnerParameters();
		this.commonParamsGuiding.addClasspath(this.classpath);
		this.commonParamsGuiding.setStateIdentificationMode(StateIdentificationMode.COMPACT);
		this.commonParamsGuiding.setBreadthMode(BreadthMode.ALL_DECISIONS);
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

	private State initialState = null;
	
	/**
	 * Performs symbolic execution of the target method guided by a test case 
	 * up to some depth, then peeks the states on the next branch.  
	 * 
	 * @param testCase a {@link TestCase}, it will guide symbolic execution.
	 * @param testDepth the maximum depth up to which {@code t} guides 
	 *        symbolic execution, or a negative value.
	 * @return a {@link List}{@code <}{@link State}{@code >} containing
	 *         all the states on branch at depth {@code stateDepth + 1}. 
	 *         In case {@code stateDepth < 0} executes the test up to the 
	 *         final state and returns a list containing only the final state.
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

		//builds the parameters
		final RunnerParameters pGuided = this.commonParamsGuided.clone();
		final RunnerParameters pGuiding = this.commonParamsGuiding.clone();

		//sets the calculator
		final CalculatorRewriting calc = new CalculatorRewriting();
		calc.addRewriter(new RewriterOperationOnSimplex());
		pGuided.setCalculator(calc);
		pGuiding.setCalculator(calc);
		
		//sets the decision procedures
		pGuided.setDecisionProcedure(new DecisionProcedureAlgorithms(
				new DecisionProcedureClassInit( //useless?
						new DecisionProcedureLICS( //useless?
								new DecisionProcedureSMTLIB2_AUFNIRA(
										new DecisionProcedureAlwSat(), 
										calc, this.z3Path + COMMANDLINE_LAUNCH_Z3), 
								calc, new LICSRulesRepo()), 
						calc, new ClassInitRulesRepo()), calc));
		pGuiding.setDecisionProcedure(
				new DecisionProcedureAlgorithms(
						new DecisionProcedureClassInit(
								new DecisionProcedureAlwSat(), 
								calc, new ClassInitRulesRepo()), 
						calc)); //for concrete execution

		//sets the guiding method (to be executed concretely)
		pGuiding.setMethodSignature(testCase.getClassName(), testCase.getParameterSignature(), testCase.getMethodName());
		//creates the guidance decision procedure and sets it
		final int numberOfHits = countNumberOfInvocation(testCase.getClassName(), targetMethod);//TODO calculate the number of hits based on the test
		//System.out.println(numberOfHits);
		final DecisionProcedureGuidance guid = new DecisionProcedureGuidance(pGuided.getDecisionProcedure(),
				pGuided.getCalculator(), pGuiding, pGuided.getMethodSignature(), numberOfHits);
		pGuided.setDecisionProcedure(guid);
		
		//sets the actions
		final ActionsRunner actions = new ActionsRunner(testDepth, guid);
		pGuided.setActions(actions);

		//builds the runner and runs it
		final RunnerBuilder rb = new RunnerBuilder();
		final Runner r = rb.build(pGuided);
		r.run();

		//outputs
		this.initialState = rb.getEngine().getInitialState();
		return actions.getStateList();
	}
	
	private static class CountVisitor extends VoidVisitorAdapter<Object> {
		final String methodName;
        int methodCallCounter = 0;
        
        public CountVisitor(String methodName) {
        	this.methodName = methodName;
        }
        
        @Override
        public void visit(MethodCallExpr n, Object arg) {
            super.visit(n, arg);
            //System.out.println(Thread.currentThread().getName()+ "_" + className + "_" +  methodName + " = " + n.getNameAsString());
            if(n.getNameAsString().equals(this.methodName)){
            	this.methodCallCounter++;
            }
        }
    }
	
	private int countNumberOfInvocation(String className, String methodName){
        final CountVisitor v = new CountVisitor(methodName);
		try {
			final FileInputStream in = new FileInputStream(outPath + "/" + className + ".java");
            v.visit(JavaParser.parse(in), null);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return v.methodCallCounter;
	}
	
	
	/**
	 * Must be invoked after an invocation of {@link #runProgram(TestCase, int)}.
	 * Returns the initial state of symbolic execution.
	 * 
	 * @return a {@link State} or {@code null} if this method is invoked
	 *         before an invocation of {@link #runProgram(TestCase, int)}.
	 */
	public State getInitialState() {
		return this.initialState;
	}
}
