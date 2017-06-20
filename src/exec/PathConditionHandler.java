package exec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.io.BufferedOutputStream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import common.Settings;
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
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.ThreadStackEmptyException;

public class PathConditionHandler {
	private PathExplorer pe;
	
	
	public void generateTestCases(RunnerPath rp, int i, int pos, State newState)
		throws DecisionException, CannotBuildEngineException, InitializationException, 
	   InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
	   ClasspathException, CannotBacktrackException, CannotManageStateException, 
	   ThreadStackEmptyException, ContradictionException, EngineStuckException, 
	   FailureException{
			
		
		String fileName = rp.getFormatter(i, newState);
		
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

		if (compiler == null) {
			//return null;
		}
		String OutputBin = Settings.BIN_PATH.toString();
		String reportDir = Settings.TMP_BASE_PATH.toString();
		
		final Path logFileJavacPath = Paths.get(reportDir + "/javac-log-" + pos + "_" + i + ".txt");

		final String[] javacParameters = { "-d", OutputBin, fileName };
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(logFileJavacPath))) {
			compiler.run(null, w, w, javacParameters);
		} catch (IOException e) {
			e.printStackTrace();
			//return null;
		}

			
		String testPackage = "exec";
			
		String testClass = "Testgen";
			
		String testMethod = "getNode(Lexec/Testgen$Node;I)Lexec/Testgen$Node;";
			
		String evosuitePath = Settings.EVOSUITE_PATH.toString();
			
		String classpath = Settings.BIN_PATH.toString() + ";" + Settings.SUSHI_LIB_PATH.toString();
								 
		
		String testDir = Settings.OUT_PATH.toString();
			
		final List<String> evosuiteParameters = new ArrayList<String>();
			
		evosuiteParameters.add("java");
		evosuiteParameters.add("-Xmx4G");
		evosuiteParameters.add("-jar");
		evosuiteParameters.add(evosuitePath);
		evosuiteParameters.add("-class");
		evosuiteParameters.add(testPackage + "." + testClass);
		evosuiteParameters.add("-mem");
		evosuiteParameters.add("2048");
		evosuiteParameters.add("-DCP=" + classpath);
		evosuiteParameters.add("-Dassertions=false");
		evosuiteParameters.add("-Dhtml=false");
		evosuiteParameters.add("-Dglobal_timeout=600");
		evosuiteParameters.add("-Dreport_dir=" + reportDir);
		evosuiteParameters.add("-Djunit_suffix=PC_" + pos + "_" + i  + "_Test");
		evosuiteParameters.add("-Dsearch_budget=600");
		evosuiteParameters.add("-Dtest_dir=" + testDir);
		evosuiteParameters.add("-Dvirtual_fs=false");
		evosuiteParameters.add("-Dselection_function=ROULETTEWHEEL");
		evosuiteParameters.add("-Dcrossover_function=SINGLEPOINT");
		evosuiteParameters.add("-Dcriterion=PATHCONDITION");		
		evosuiteParameters.add("-Davoid_replicas_of_individuals=true"); 
		evosuiteParameters.add("-Dsushi_statistics=true");
		evosuiteParameters.add("-Dcrossover_implementation=SUSHI_HYBRID");
		evosuiteParameters.add("-Duse_minimizer_during_crossover=true");
		evosuiteParameters.add("-Dno_change_iterations_before_reset=30");
		evosuiteParameters.add("-Dmax_size=1");
		evosuiteParameters.add("-Dmax_initial_tests=1");
		evosuiteParameters.add("-Dinline=false");
		evosuiteParameters.add("-Dpath_condition=" + testPackage + "." + testClass + "," + testMethod + ",EvoSuiteWrapper_" + i);
		evosuiteParameters.add("-Dpath_condition_check_first_target_call_only=true");
			
		final Path logFileEvosuitePath = Paths.get(reportDir + "/evosuite-log-" + pos + "_" + i + ".txt");
		final ProcessBuilder pb = new ProcessBuilder(evosuiteParameters).redirectErrorStream(true).redirectOutput(logFileEvosuitePath.toFile());
		Process pr;
		try {
			pr = pb.start();
			pr.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			//return null;
		}
		String testCaseScaff = testDir +"/exec/" + testClass + "PC_" + pos + "_" + i + "_Test_scaffolding.java";
		String testCase = testDir + "/exec/" + testClass + "PC_" +  pos + "_" + i + "_Test.java";
			
		final Path logFileJavacPath_Test = Paths.get(reportDir + "/javac-log-test-" +  pos + "_" + i + ".txt");
		final String[] javacParametersTestScaff = { "-d", OutputBin, testCaseScaff };
		final String[] javacParametersTestCase = { "-d", OutputBin, testCase };
			
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(logFileJavacPath_Test))) {
			compiler.run(null, w, w, javacParametersTestScaff);
			compiler.run(null, w, w, javacParametersTestCase);
		} catch (IOException e) {
			e.printStackTrace();
			//return null;
		}
		
		 
		 TestCase newTC = new TestCase( testPackage + "/" + testClass + "PC_" +  pos + "_" + i + "_Test", "()V", "test0");
		 System.out.println("RECURSE on " + newTC);
		 pe = new PathExplorer(rp);
		 pe.explore(pos+1, newState.getDepth()-1, newTC);
		 
		 
	}
	
		
}
	
	

