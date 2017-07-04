package exec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
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
import sushi.execution.jbse.StateFormatterSushiPathCondition;

public class PathConditionHandler {
	private Options o;
	private String testClass;
	private String testMethod;
	private String testSignature;
	private String binPath;
	private String tmpPath;
	private String evosuitePath;
	private String sushiLibPath;
	private String outPath;
	private final JavaCompiler compiler;
	

	public PathConditionHandler(Options o) {
		this.o = o;
		this.testClass = o.getGuidedMethod().get(0);
		this.testSignature = o.getGuidedMethod().get(1);
		this.testMethod = o.getGuidedMethod().get(2);
		this.binPath = o.getBinPath().toString();
		this.tmpPath = o.getTmpDirectoryBase().toString();
		this.evosuitePath = o.getEvosuitePath().toString();
		this.sushiLibPath = o.getSushiLibPath().toString();
		this.outPath = o.getOutDirectory().toString();
		this.compiler = ToolProvider.getSystemJavaCompiler();
		if (this.compiler == null) {
			//TODO throw an exception
		}
	}
	

	/**
	 * Generates the EvoSuite wrapper for the path condition of some state.
	 * 
	 * @param testCount a {@link TestIdentifier} (used only to disambiguate
	 *        file names).
	 * @param initialState a {@link State}; must be the initial state in the execution 
	 *        for which we want to generate the wrapper.
	 * @param finalState a {@link State}; must be the final state in the execution 
	 *        for which we want to generate the wrapper.
	 * @return a {@link String}, the file name of the generated EvoSuite wrapper.
	 */
	private String emitEvoSuiteWrapper(TestIdentifier testCount, State initialState, State finalState) {
		final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount.getTestCount(), () -> initialState);
		fmt.formatPrologue();
		fmt.formatState(finalState);
		fmt.formatEpilogue();

		final String fileName = this.tmpPath + "/EvoSuiteWrapper_" + testCount.getTestCount() +".java";
		try (final BufferedWriter w = Files.newBufferedWriter(Paths.get(fileName))) {
			w.write(fmt.emit());
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		fmt.cleanup();

		return fileName;
	}


	/**
	 * Invokes EvoSuite to generate a {@link TestCase} that covers a path 
	 * condition, and then explores the generated test case starting from
	 * the depth of the path condition.
	 * 
	 * @param testCount a {@link TestIdentifier}. Only used to disambiguate file names.
	 * @param initialState a {@link State}, the initial state of a symbolic execution.
	 * @param finalState a {@link State}, the final state of the symbolic execution whose
	 *        initial state is {@code initialState}. The test case will cover its path condition.
	 * @param depth the depth of {@code state}.
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
	public void generateTestCases(TestIdentifier testCount, State initialState, State finalState, int depth)
			throws DecisionException, CannotBuildEngineException, InitializationException, 
			InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
			ClasspathException, CannotBacktrackException, CannotManageStateException, 
			ThreadStackEmptyException, ContradictionException, EngineStuckException, 
			FailureException {
		//compiles the Evosuite wrapper
		final String fileName = emitEvoSuiteWrapper(testCount, initialState, finalState);
		final Path logFileJavacPath = Paths.get(this.tmpPath + "/javac-log-" + testCount.getTestCount() + ".txt");
		final String[] javacParameters = { "-d", binPath, fileName };
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(logFileJavacPath))) {
			this.compiler.run(null, w, w, javacParameters);
		} catch (IOException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}

		//some configuration - test method, paths
		final String classpathEvosuite = this.binPath + File.pathSeparator + this.sushiLibPath;
		final String classpathCompilation = classpathEvosuite + File.pathSeparator + this.evosuitePath;
		
		//prepares the Evosuite parameters
		final List<String> evosuiteParameters = new ArrayList<String>();
		evosuiteParameters.add("java");
		evosuiteParameters.add("-Xmx4G");
		evosuiteParameters.add("-jar");
		evosuiteParameters.add(this.evosuitePath);
		evosuiteParameters.add("-class");
		evosuiteParameters.add(this.testClass.replace('/', '.'));
		evosuiteParameters.add("-mem");
		evosuiteParameters.add("2048");
		evosuiteParameters.add("-DCP=" + classpathEvosuite); 
		evosuiteParameters.add("-Dassertions=false");
		evosuiteParameters.add("-Dhtml=false");
		evosuiteParameters.add("-Dglobal_timeout=600");
		evosuiteParameters.add("-Dreport_dir=" + this.tmpPath);
		evosuiteParameters.add("-Djunit_suffix=" + "_" + testCount.getTestCount()  + "_Test");
		evosuiteParameters.add("-Dsearch_budget=600");
		evosuiteParameters.add("-Dtest_dir=" + outPath);
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
		evosuiteParameters.add("-Dpath_condition=" + this.testClass.replace('/', '.') + "," + this.testMethod + this.testSignature + ",EvoSuiteWrapper_" + testCount.getTestCount());
		evosuiteParameters.add("-Dpath_condition_check_first_target_call_only=true");

		//launches Evosuite
		final Path logFileEvosuitePath = Paths.get(this.tmpPath + "/evosuite-log-" + testCount.getTestCount() + ".txt");
		final ProcessBuilder pb = new ProcessBuilder(evosuiteParameters).redirectErrorStream(true).redirectOutput(logFileEvosuitePath.toFile());
		try {
			final Process pr = pb.start();
			pr.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}
		
		//compiles the generated test
		//TODO here we are assuming EvoSuite was able to find a test, handle the situation where EvoSuite does not generate anything
		final String testCaseClassName = this.testClass + "_" + testCount.getTestCount() + "_Test";
		final String testCaseScaff = this.outPath + "/" + testCaseClassName + "_scaffolding.java";
		final String testCase = this.outPath + "/" + testCaseClassName + ".java";
		final Path javacLogFile = Paths.get(this.tmpPath + "/javac-log-test-" +  testCount.getTestCount() + ".txt");
		final String[] javacParametersTestScaff = { "-cp", classpathCompilation, "-d", this.binPath, testCaseScaff };
		final String[] javacParametersTestCase = { "-cp", classpathCompilation, "-d", this.binPath, testCase };
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFile))) {
			this.compiler.run(null, w, w, javacParametersTestScaff);
			this.compiler.run(null, w, w, javacParametersTestCase);
		} catch (IOException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}

		//creates the TestCase and explores it
		final TestCase newTC = new TestCase(testCaseClassName, "()V", "test0");
		System.out.println("RECURSE on " + newTC);
		final PathExplorer pe = new PathExplorer(this.o);
		pe.explore(testCount, newTC, depth + 1);
	}
}



