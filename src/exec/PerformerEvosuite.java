package exec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
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

import concurrent.InputBuffer;
import concurrent.OutputBuffer;
import concurrent.Performer;
import jbse.mem.State;
import sushi.execution.jbse.StateFormatterSushiPathCondition;

public class PerformerEvosuite extends Performer<JBSEResult, EvosuiteResult> {
	private final String targetClass;
	private final String targetSignature;
	private final String targetMethod;
	private final String binPath;
	private final String tmpPath;
	private final String evosuitePath;
	private final String sushiLibPath;
	private final String outPath;
	private final int timeBudget;
	private final boolean useMOSA;
	private final TestIdentifier testIdentifier;
	private final JavaCompiler compiler;

	public PerformerEvosuite(Options o, InputBuffer<JBSEResult> in, OutputBuffer<EvosuiteResult> out) {
		super(in, out, o.getTimeoutMOSATaskCreationDuration(), o.getTimeoutMOSATaskCreationUnit(), o.getNumOfThreads(), (o.getUseMOSA() ? o.getNumMOSATargets() : 1));
		this.targetClass = o.getTargetMethod().get(0);
		this.targetSignature = o.getTargetMethod().get(1);
		this.targetMethod = o.getTargetMethod().get(2);
		this.binPath = o.getBinPath().toString();
		this.tmpPath = o.getTmpDirectoryBase().toString();
		this.evosuitePath = o.getEvosuitePath().toString();
		this.sushiLibPath = o.getSushiLibPath().toString();
		this.outPath = o.getOutDirectory().toString();
		this.timeBudget = o.getEvosuiteBudget();
		this.useMOSA = o.getUseMOSA();
		this.testIdentifier = new TestIdentifier();
		this.compiler = ToolProvider.getSystemJavaCompiler();
		if (this.compiler == null) {
			System.out.println("[EVOSUITE] Failed to find a system Java compiler. Did you install a JDK?");
			//TODO throw an exception
		}
	}

	@Override
	protected Runnable makeJob(List<JBSEResult> items) {
		final int testCountInitial = this.testIdentifier.getTestCount();
		this.testIdentifier.testCountAdd(items.size());
		final Runnable job = () -> generateTestsAndScheduleJBSE(testCountInitial, items);
		return job;
	}

	/**
	 * Invokes EvoSuite to generate a set of {@link TestCase}s that cover a 
	 * set of path condition, and then explores the generated test cases 
	 * starting from the depth of the respective path conditions.
	 * 
	 * @param testCountInitial an {@code int}, the number used to identify 
	 *        the generated tests. The test generated from {@code items.get(i)}
	 *        will be numbered {@code testCountInitial + i}.
	 * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}, results of symbolic execution.
	 */
	private void generateTestsAndScheduleJBSE(int testCountInitial, List<JBSEResult> items) {
		if (!this.useMOSA && items.size() != 1) {
			System.out.println("[EVOSUITE] Unexpected internal error: MOSA is not used but the number of targets passed to EvoSuite is different from 1");
			return; //TODO throw an exception
		}
		
		//generates and compiles the wrappers
		emitAndCompileEvoSuiteWrappers(testCountInitial, items);

		//builds the EvoSuite command line
		final List<String> evosuiteCommand = buildEvoSuiteCommand(testCountInitial, items); 

		//launches EvoSuite
		launchEvoSuite(testCountInitial, evosuiteCommand);

		//launches the threads that wait for the tests emitted by EvoSuite
		//and schedule JBSE for exploring them
		int i = testCountInitial;
		for (JBSEResult item : items) {
			final int cur = i; //to keep the compiler happy
			new Thread(() -> detectTestAndScheduleJBSE(cur, item)).start();
			++i;
		}
	}

	/**
	 * Emits the EvoSuite wrapper (file .java) for the path condition of some state.
	 * 
	 * @param testCount an {@code int}, the number used to identify the test.
	 * @param initialState a {@link State}; must be the initial state in the execution 
	 *        for which we want to generate the wrapper.
	 * @param finalState a {@link State}; must be the final state in the execution 
	 *        for which we want to generate the wrapper.
	 * @return a {@link Path}, the file path of the generated EvoSuite wrapper.
	 */
	private Path emitEvoSuiteWrapper(int testCount, State initialState, State finalState) {
		final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount, () -> initialState);
		fmt.formatPrologue();
		fmt.formatState(finalState);
		fmt.formatEpilogue();

		final Path wrapperFilePath = Paths.get(this.tmpPath + "/EvoSuiteWrapper_" + testCount + ".java");
		try (final BufferedWriter w = Files.newBufferedWriter(wrapperFilePath)) {
			w.write(fmt.emit());
		} catch (IOException e) {
			System.out.println("[EVOSUITE] Unexpected I/O error while creating EvoSuite wrapper " + wrapperFilePath.toString() + ": " + e.getMessage());
			//TODO throw an exception
		}
		fmt.cleanup();

		return wrapperFilePath;
	}
	
	/**
	 * Emits and compiles all the EvoSuite wrappers.
	 * 
	 * @param testCountInitial an {@code int}, the number used to identify 
	 *        the generated tests. The test generated from {@code items.get(i)}
	 *        will be numbered {@code testCountInitial + i}.
	 * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}, results of symbolic execution.
	 */
	private void emitAndCompileEvoSuiteWrappers(int testCountInitial, List<JBSEResult> items) {
		final String classpathCompilationWrapper = this.binPath + File.pathSeparator + this.sushiLibPath;
		int i = testCountInitial;
		for (JBSEResult item : items) {
			final State initialState = item.getInitialState();
			final State finalState = item.getFinalState();
			final Path wrapperFilePath = emitEvoSuiteWrapper(i, initialState, finalState);
			final Path javacLogFilePath = Paths.get(this.tmpPath + "/javac-log-" + i + ".txt");
			final String[] javacParameters = { "-cp", classpathCompilationWrapper, "-d", this.tmpPath, wrapperFilePath.toString() };
			try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
				this.compiler.run(null, w, w, javacParameters);
			} catch (IOException e) {
				System.out.println("[EVOSUITE] Unexpected I/O error while creating compilation log file " + javacLogFilePath.toString() + ": " + e.getMessage());
				//TODO throw an exception
			}
			++i;
		}
	}

	/**
	 * Builds the command line for invoking EvoSuite.
	 * 
	 * @param testCountInitial an {@code int}, the number used to identify 
	 *        the generated tests. The test generated from {@code items.get(i)}
	 *        will be numbered {@code testCountInitial + i}.
	 * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}, results of symbolic execution.
	 * @return a command line in the format of a {@link List}{@code <}{@link String}{@code >},
	 *         suitable to be passed to a {@link ProcessBuilder}.
	 */
	private List<String> buildEvoSuiteCommand(int testCountInitial, List<JBSEResult> items) {
		final String classpathEvosuite = this.binPath + File.pathSeparator + this.sushiLibPath + File.pathSeparator + this.tmpPath;
		final List<String> retVal = new ArrayList<String>();
		retVal.add("java");
		retVal.add("-Xmx4G");
		retVal.add("-jar");
		retVal.add(this.evosuitePath);
		retVal.add("-class");
		retVal.add(this.targetClass.replace('/', '.'));
		retVal.add("-mem");
		retVal.add("2048");
		retVal.add("-DCP=" + classpathEvosuite); 
		retVal.add("-Dassertions=false");
		retVal.add("-Dglobal_timeout=" + this.timeBudget);
		retVal.add("-Dreport_dir=" + this.tmpPath);
		retVal.add("-Dsearch_budget=" + this.timeBudget);
		retVal.add("-Dtest_dir=" + outPath);
		retVal.add("-Dvirtual_fs=false");
		retVal.add("-Dselection_function=ROULETTEWHEEL");
		retVal.add("-Dcriterion=PATHCONDITION");		
		retVal.add("-Dsushi_statistics=true");
		retVal.add("-Dinline=false");
		//retVal.add("-Dsushi_modifiers_local_search=true"); does not work
		retVal.add("-Dpath_condition_target=LAST_ONLY");
		if (this.useMOSA) {
			retVal.add("-Duse_minimizer_during_crossover=false"); //TODO temporary until Giovanni implements minimization during crossover
			retVal.add("-Dcrossover_function=SUSHI_HYBRID");
			retVal.add("-Dalgorithm=DYNAMOSA");
			retVal.add("-generateMOSuite");
			final StringBuilder optionPC = new StringBuilder("-Dpath_condition=");
			for (int i = testCountInitial; i < testCountInitial + items.size(); ++i) {
				if (i > testCountInitial) {
					optionPC.append(":");
				}
				optionPC.append(this.targetClass.replace('/', '.') + "," + this.targetMethod + this.targetSignature + ",EvoSuiteWrapper_" + i);
			}
			retVal.add(optionPC.toString());
		} else {
			retVal.add("-Djunit_suffix=" + "_" + testCountInitial  + "_Test");
			retVal.add("-Dhtml=false");
			retVal.add("-Duse_minimizer_during_crossover=true");
			retVal.add("-Dcrossover_function=SINGLEPOINT");
			retVal.add("-Dcrossover_implementation=SUSHI_HYBRID");
			retVal.add("-Dno_change_iterations_before_reset=30");
			retVal.add("-Davoid_replicas_of_individuals=true"); 
			retVal.add("-Dmax_size=1");
			retVal.add("-Dmax_initial_tests=1");
			retVal.add("-Dpath_condition=" + this.targetClass.replace('/', '.') + "," + this.targetMethod + this.targetSignature + ",EvoSuiteWrapper_" + testCountInitial);
		}
		
		return retVal;
	}
	
	private void launchEvoSuite(int testCountInitial, List<String> evosuiteCommand) {
		final Path evosuiteLogFilePath = Paths.get(this.tmpPath + "/evosuite-log-" + testCountInitial + ".txt");
		final ProcessBuilder pb = new ProcessBuilder(evosuiteCommand).redirectErrorStream(true).redirectOutput(evosuiteLogFilePath.toFile());
		try {
			final Process pr = pb.start();
			pr.waitFor(); //TODO delete this when detectTestAndScheduleJBSE will be able to synchronize with EvoSuite
		} catch (IOException | InterruptedException e) {
			System.out.println("[EVOSUITE] Unexpected I/O error while running EvoSuite: " + e.getMessage());
			//TODO throw an exception
		}
	}
	
	/**
	 * Checks that an emitted test class has the {@code test0} method,
	 * to filter out the cases where EvoSuite fails but emits the test class.
	 * 
	 * @param className a {@link String}, the name of the test class.
	 * @throws NoSuchMethodException if the class {@code className} has not
	 *         a {@code void test0()} method.
	 */
	private void checkTestExists(String className) throws NoSuchMethodException {
		try {
			final URLClassLoader cloader = URLClassLoader.newInstance(new URL[]{Paths.get(binPath).toUri().toURL()}); 
			cloader.loadClass(className.replace('/',  '.')).getDeclaredMethod("test0");
		} catch (SecurityException | ClassNotFoundException | MalformedURLException e) {
			System.out.println("[EVOSUITE] Unexpected error while verifying that class " + className + " exists and has a test method: " + e.getMessage());
			//TODO throw an exception
		} 			
	}
	
	/**
	 * Waits for EvoSuite to emit a test class and schedules JBSE
	 * for its further analysis.
	 * 
	 * @param testCount an {@code int}, the number used to identify the test.
	 * @param item the {@link JBSEResult} used to build the test with number
	 *        {@code testCount}.
	 */
	private void detectTestAndScheduleJBSE(int testCount, JBSEResult item) {
		final State finalState = item.getFinalState();
		final int depth = item.getDepth();
		
		//TODO synchronize with EvoSuite/MOSA!
		
		//checks if EvoSuite generated a test, exits in the negative case
		final String testCaseClassName = this.targetClass + "_" + testCount + "_Test";
		final String testCaseScaff = this.outPath + "/" + testCaseClassName + "_scaffolding.java";
		final String testCase = this.outPath + "/" + testCaseClassName + ".java";
		if (!new File(testCase).exists() || !new File(testCaseScaff).exists()) {
			return;
		}
		
		//compiles the generated test
		final String classpathCompilationTest = this.binPath + File.pathSeparator + this.sushiLibPath + File.pathSeparator + this.evosuitePath;
		final Path javacLogFilePath = Paths.get(this.tmpPath + "/javac-log-test-" +  testCount + ".txt");
		final String[] javacParametersTestScaff = { "-cp", classpathCompilationTest, "-d", this.binPath, testCaseScaff };
		final String[] javacParametersTestCase = { "-cp", classpathCompilationTest, "-d", this.binPath, testCase };
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
			this.compiler.run(null, w, w, javacParametersTestScaff);
			this.compiler.run(null, w, w, javacParametersTestCase);
		} catch (IOException e) {
			System.out.println("[EVOSUITE] Unexpected I/O error while creating compilation log file " + javacLogFilePath.toString() + ": " + e.getMessage());
			//TODO throw an exception
		}
		
		//creates the TestCase and schedules it for further exploration
		try {
			checkTestExists(testCaseClassName);
			System.out.println("[EVOSUITE] Generated test case " + testCaseClassName + ", depth: " + depth + ", path condition: " + finalState.getPathCondition());
			final TestCase newTC = new TestCase(testCaseClassName, "()V", "test0");
			this.getOutputBuffer().add(new EvosuiteResult(newTC, depth + 1));
		} catch (NoSuchMethodException e) { 
			//EvoSuite failed to generate the test case, thus we just ignore it 
			System.out.println("[EVOSUITE] Failed to generate the test case " + testCaseClassName + " for PC: " + finalState.getPathCondition());
		}
	}
}
