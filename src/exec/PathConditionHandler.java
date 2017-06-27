package exec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.io.BufferedOutputStream;
import java.io.File;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

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
	private final RunnerPath rp;
	private final JavaCompiler compiler;
	private PathExplorer pe;
	private Options o;
	private String test_Package;
	private String test_Class;
	private String test_Method;
	private String test_Signature;
	private String bin_Path;
	private String tmp_Path;
	private String evosuite_Path;
	private String sushi_lib_Path;
	private String out_Path;
	

	public PathConditionHandler(Options o, RunnerPath rp) {
		this.rp = rp;
		this.o = o;
		this.test_Package = o.getGuidedMethod().get(0);
		this.test_Class = o.getGuidedMethod().get(1);
		this.test_Signature = o.getGuidedMethod().get(2);
		this.test_Method = o.getGuidedMethod().get(3);
		this.bin_Path = o.getBinPath().toString();
		this.tmp_Path = o.getTmpDirectoryBase().toString();
		this.evosuite_Path = o.getEvosuitePath().toString();
		this.sushi_lib_Path = o.getSushiLibPath().toString();
		this.out_Path = o.getOutDirectory().toString();
		this.compiler = ToolProvider.getSystemJavaCompiler();
		if (this.compiler == null) {
			//TODO throw an exception
		}
	}

	/**
	 * Invokes EvoSuite to generate a {@link TestCase} that covers a path 
	 * condition, and then explores the generated test case starting from
	 * the depth of the path condition.
	 * 
	 * @param state a {@link State}. The test case will cover its path condition.
	 * @param depth the depth of {@code state}.
	 * @param breadth the breadth of {@code state}. Only used to disambiguate file names.
	 * @param maxDepth the maximum depth at which generation of tests is performed.
	 *        Used for recursive calls to exploration.
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
	public void generateTestCases(State state, int depth, int breadth, int maxDepth)
			throws DecisionException, CannotBuildEngineException, InitializationException, 
			InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
			ClasspathException, CannotBacktrackException, CannotManageStateException, 
			ThreadStackEmptyException, ContradictionException, EngineStuckException, 
			FailureException{
		//compiles the Evosuite wrapper
		final String fileName = rp.emitEvoSuiteWrapper(state, breadth);
		final String outputBin = bin_Path;
		final String reportDir = tmp_Path;
		final Path logFileJavacPath = Paths.get(reportDir + "/javac-log-" + depth + "_" + breadth + ".txt");
		final String[] javacParameters = { "-d", outputBin, fileName };
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(logFileJavacPath))) {
			compiler.run(null, w, w, javacParameters);
		} catch (IOException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}

		//some configuration - test method, paths
		//TODO make this stuff configurable!
		final String testPackage = test_Package;
		final String testClass = test_Class;
		final String testMethod = test_Method + test_Signature;
		final String evosuitePath = evosuite_Path;
		final String classpathEvosuite = bin_Path + File.pathSeparator + sushi_lib_Path;
		final String classpathCompilation = classpathEvosuite + File.pathSeparator + evosuitePath;
		final String testDir = out_Path;
		
		//prepares the Evosuite parameters
		final List<String> evosuiteParameters = new ArrayList<String>();
		evosuiteParameters.add("java");
		evosuiteParameters.add("-Xmx4G");
		evosuiteParameters.add("-jar");
		evosuiteParameters.add(evosuitePath);
		evosuiteParameters.add("-class");
		evosuiteParameters.add(testPackage + "." + testClass);
		evosuiteParameters.add("-mem");
		evosuiteParameters.add("2048");
		evosuiteParameters.add("-DCP=" + classpathEvosuite);
		evosuiteParameters.add("-Dassertions=false");
		evosuiteParameters.add("-Dhtml=false");
		evosuiteParameters.add("-Dglobal_timeout=600");
		evosuiteParameters.add("-Dreport_dir=" + reportDir);
		evosuiteParameters.add("-Djunit_suffix=PC_" + depth + "_" + breadth  + "_Test");
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
		evosuiteParameters.add("-Dpath_condition=" + testPackage + "." + testClass + "," + testMethod + ",EvoSuiteWrapper_" + breadth);
		evosuiteParameters.add("-Dpath_condition_check_first_target_call_only=true");

		//launches Evosuite
		final Path logFileEvosuitePath = Paths.get(reportDir + "/evosuite-log-" + depth + "_" + breadth + ".txt");
		final ProcessBuilder pb = new ProcessBuilder(evosuiteParameters).redirectErrorStream(true).redirectOutput(logFileEvosuitePath.toFile());
		Process pr;
		try {
			pr = pb.start();
			pr.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}
		
		//compiles the generated test
		//TODO here we are assuming EvoSuite was able to find a test, handle the situation where EvoSuite does not generate anything
		//TODO make the following paths configurable!
		final String testCaseScaff = testDir + "/" + testPackage + "/" + testClass + "PC_" + depth + "_" + breadth + "_Test_scaffolding.java";
		final String testCase = testDir + "/" + testPackage + "/" + testClass + "PC_" +  depth + "_" + breadth + "_Test.java";
		final Path logFileJavacPath_Test = Paths.get(reportDir + "/javac-log-test-" +  depth + "_" + breadth + ".txt");
		final String[] javacParametersTestScaff = { "-cp", classpathCompilation, "-d", outputBin, testCaseScaff };
		final String[] javacParametersTestCase = { "-cp", classpathCompilation, "-d", outputBin, testCase };
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(logFileJavacPath_Test))) {
			compiler.run(null, w, w, javacParametersTestScaff);
			compiler.run(null, w, w, javacParametersTestCase);
		} catch (IOException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}

		//creates the TestCase and explores it
		final TestCase newTC = new TestCase( testPackage + "/" + testClass + "PC_" +  depth + "_" + breadth + "_Test", "()V", "test0");
		System.out.println("RECURSE on " + newTC);
		pe = new PathExplorer(o, rp);
		pe.explore(newTC, depth + 1, maxDepth);
	}
}



