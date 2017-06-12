package exec;

import java.util.ArrayList;
import java.util.List;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import common.Settings;
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
import jbse.jvm.Engine;
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
	private int numBranches;
	private String classPackage = "exec";
	private String className = "Testgen";
	private String parametersSignature = "(Lexec/Testgen$Node;I)Lexec/Testgen$Node;";
	private String methodName  = "getNode";
	
	private Engine e;
	private DecisionProcedureGuidance dp;
	
	private String[] classpath = {
			Settings.BIN_PATH.toString(), 
			Settings.JBSE_PATH.toString(), 
			Settings.JBSE_PATH.toString() + "/../data/jre/rt.jar" //indispensable!
			//put all the paths to the program to be executed (the binaries)
	};
	
	

	private  RunnerParameters commonParams = null;
	
	private RunnerParameters commonGuidance = null;
	
	private  RunnerParameters getCommonParams() throws DecisionException {
		if (commonParams == null) {
			//builds the parameters for the guided (symbolic) execution
			RunnerParameters p = new RunnerParameters();
			
			//the guided method (to be executed symbolically)
			String subjectClassName = classPackage + "/" + className;
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
											calc, Settings.Z3_PATH.toString() + " -smt2 -in -t:10"), 
									calc, new LICSRulesRepo()), 
							calc, new ClassInitRulesRepo()), calc);
			p.setDecisionProcedure(pd);
			p.setBreadthMode(BreadthMode.ALL_DECISIONS_NONTRIVIAL);
			
			commonParams = p;

		}
		return commonParams;
	}
	
	private  RunnerParameters getCommonGuidance(RunnerParameters p) throws DecisionException {
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
	

		
	public List<State> runProgram(TestCase t, int stateDepth)
		throws DecisionException, CannotBuildEngineException, InitializationException, 
		InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
		ClasspathException, CannotBacktrackException, CannotManageStateException, 
		ThreadStackEmptyException, ContradictionException, EngineStuckException, 
		FailureException {
			
			//builds the parameters for the guided (symbolic) execution
			RunnerParameters p = getCommonParams();
			
			RunnerParameters pGuidance = getCommonGuidance(p);
			
			//the guiding method (to be executed concretely)
			String testClassName = t.getClassName() ;
			String testParametersSignature = t.getParameterSignature();
			String testMethodName = t.getMethodName();
			
	
			pGuidance.setMethodSignature(testClassName, testParametersSignature, testMethodName);

			DecisionProcedureGuidance guid = new DecisionProcedureGuidance(p.getDecisionProcedure(),
					p.getCalculator(), pGuidance, p.getMethodSignature());
			p.setDecisionProcedure(guid);
			
			
			
			List<State> stateList = new ArrayList<State>();
			numBranches = 0;
			
			p.setActions(new Actions() {
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
					if(stateDepth == 0){
						guid.endGuidance();
					}
					
					return super.atRoot();
				}
				
				@Override
				public boolean atBranch(BranchPoint bp) {
					numBranches++;				
					if(numBranches == stateDepth){
						guid.endGuidance();
					} else if (numBranches == stateDepth + 1) {
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
					if(stateDepth < 0){
						final State finalState = this.getEngine().getCurrentState();
						stateList.add(finalState);
					}

					super.atEnd();
				}
				
			});
			
			//builds the runner
			RunnerBuilder rb = new RunnerBuilder();
			Runner r = rb.build(p);
			r.run();
			
			e = rb.getEngine();
			dp = guid;
			
			return stateList;
			
	}
	
	public String getFormatter(int i, State newState){
		StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(i, e::getInitialState, () -> {
			try {
				return dp.getModel();
			} catch (DecisionException e1) {
				return null;
			}
	});
		fmt.formatPrologue();
		fmt.formatState(newState);
		fmt.formatEpilogue();
		
		//String srcPath = Settings.SRC_PATH.toString() + "/exec";
		
		String wrapperPath = Settings.TMP_BASE_PATH.toString();
		
		
		String fileName = wrapperPath + "/EvoSuiteWrapper_" + i +".java";
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
