package exec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.nio.file.Files;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

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
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.ThreadStackEmptyException;
import sushi.execution.jbse.StateFormatterSushiPathCondition;

public class PathConditionHandler extends Performer<JBSEResult, EvosuiteResult>{
	private final String guidedClass;
	private final String guidedSignature;
	private final String guidedMethod;
	private final String binPath;
	private final String tmpPath;
	private final String evosuitePath;
	private final String sushiLibPath;
	private final String outPath;
	private final TestIdentifier testCount;


	public PathConditionHandler(Options o, LinkedBlockingQueue<JBSEResult> in, LinkedBlockingQueue<EvosuiteResult> out) {
		super(in, out, o.getNumOfThreads());
		this.guidedClass = o.getGuidedMethod().get(0);
		this.guidedSignature = o.getGuidedMethod().get(1);
		this.guidedMethod = o.getGuidedMethod().get(2);
		this.binPath = o.getBinPath().toString();
		this.tmpPath = o.getTmpDirectoryBase().toString();
		this.evosuitePath = o.getEvosuitePath().toString();
		this.sushiLibPath = o.getSushiLibPath().toString();
		this.outPath = o.getOutDirectory().toString();
		this.testCount = new TestIdentifier();
	}


	/**
	 * Generates the EvoSuite wrapper for the path condition of some state.
	 * 
	 * @param testCount an {@code int}, the number of the test (used only to disambiguate
	 *        file names).
	 * @param initialState a {@link State}; must be the initial state in the execution 
	 *        for which we want to generate the wrapper.
	 * @param finalState a {@link State}; must be the final state in the execution 
	 *        for which we want to generate the wrapper.
	 * @return a {@link String}, the file name of the generated EvoSuite wrapper.
	 */
	private String emitEvoSuiteWrapper(int testCount, State initialState, State finalState) {
		final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount, () -> initialState);
		fmt.formatPrologue();
		fmt.formatState(finalState);
		fmt.formatEpilogue();

		final String fileName = this.tmpPath + "/EvoSuiteWrapper_" + testCount + ".java";
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
	 * @param testCount an {@code int}, the number of the test. Only used to disambiguate file names.
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
	public void generateTestCases(State initialState, State finalState, int depth, int testCount)
			throws DecisionException, CannotBuildEngineException, InitializationException, 
			InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
			ClasspathException, CannotBacktrackException, CannotManageStateException, 
			ThreadStackEmptyException, ContradictionException, EngineStuckException, 
			FailureException {
		//generates and compiles the Evosuite wrapper
		final String fileName = emitEvoSuiteWrapper(testCount, initialState, finalState);
		final Path logFileJavacPath = Paths.get(this.tmpPath + "/javac-log-" + testCount + ".txt");
		final String[] javacParameters = { "-d", binPath, fileName };
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			//TODO throw an exception
		}
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(logFileJavacPath))) {
			compiler.run(null, w, w, javacParameters);
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
		evosuiteParameters.add(this.guidedClass.replace('/', '.'));
		evosuiteParameters.add("-mem");
		evosuiteParameters.add("2048");
		evosuiteParameters.add("-DCP=" + classpathEvosuite); 
		evosuiteParameters.add("-Dassertions=false");
		evosuiteParameters.add("-Dhtml=false");
		evosuiteParameters.add("-Dglobal_timeout=600");
		evosuiteParameters.add("-Dreport_dir=" + this.tmpPath);
		evosuiteParameters.add("-Djunit_suffix=" + "_" + testCount  + "_Test");
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
		evosuiteParameters.add("-Dpath_condition=" + this.guidedClass.replace('/', '.') + "," + this.guidedMethod + this.guidedSignature + ",EvoSuiteWrapper_" + testCount);
		evosuiteParameters.add("-Dpath_condition_check_first_target_call_only=true");

		//launches Evosuite
		final Path logFileEvosuitePath = Paths.get(this.tmpPath + "/evosuite-log-" + testCount + ".txt");
		final ProcessBuilder pb = new ProcessBuilder(evosuiteParameters).redirectErrorStream(true).redirectOutput(logFileEvosuitePath.toFile());
		try {
			final Process pr = pb.start();
			pr.waitFor();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}

		//checks if EvoSuite generated a test, exits in the negative case
		final String testCaseClassName = this.guidedClass + "_" + testCount + "_Test";
		final String testCaseScaff = this.outPath + "/" + testCaseClassName + "_scaffolding.java";
		final String testCase = this.outPath + "/" + testCaseClassName + ".java";
		if (!new File(testCase).exists() || !new File(testCaseScaff).exists()) {
			return;
		}
		
		//compiles the generated test
		final Path javacLogFile = Paths.get(this.tmpPath + "/javac-log-test-" +  testCount + ".txt");
		final String[] javacParametersTestScaff = { "-cp", classpathCompilation, "-d", this.binPath, testCaseScaff };
		final String[] javacParametersTestCase = { "-cp", classpathCompilation, "-d", this.binPath, testCase };
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFile))) {
			compiler.run(null, w, w, javacParametersTestScaff);
			compiler.run(null, w, w, javacParametersTestCase);
		} catch (IOException e) {
			e.printStackTrace();
			//TODO rethrow or handle the error otherwise
		}
		
		System.out.println("Generated test case " + testCaseClassName + ", depth: " + depth + ", path condition: " + finalState.getPathCondition());
		
		//creates the TestCase and schedules it for further exploration
		final TestCase newTC = new TestCase(testCaseClassName, "()V", "test0");
		this.getOutputQueue().add(new EvosuiteResult(newTC, depth + 1));
	}

	@Override
	protected Runnable makeJob(JBSEResult item) {
		final int testCount = this.testCount.getTestCount();
		this.testCount.testIncrease();
		final Runnable job = () -> {
			try {
				generateTestCases(item.getInitialState(), item.getFinalState(), item.getDepth(), testCount);
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



