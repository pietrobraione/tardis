package exec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
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
	private final String binPath;
	private final String tmpPath;
	private final String evosuitePath;
	private final String sushiLibPath;
	private final String outPath;
	private final long timeBudgetSeconds;
	private final boolean useMOSA;
	private final TestIdentifier testIdentifier;
	private final JavaCompiler compiler;

	public PerformerEvosuite(Options o, InputBuffer<JBSEResult> in, OutputBuffer<EvosuiteResult> out) {
		super(in, out, o.getNumOfThreads(), (o.getUseMOSA() ? o.getNumMOSATargets() : 1), o.getTimeoutMOSATaskCreationDuration(), o.getTimeoutMOSATaskCreationUnit());
		this.binPath = o.getBinPath().toString();
		this.tmpPath = o.getTmpDirectoryBase().toString();
		this.evosuitePath = o.getEvosuitePath().toString();
		this.sushiLibPath = o.getSushiLibPath().toString();
		this.outPath = o.getOutDirectory().toString();
		this.timeBudgetSeconds = o.getEvosuiteTimeBudgetUnit().toSeconds(o.getEvosuiteTimeBudgetDuration());
		this.useMOSA = o.getUseMOSA();
		this.testIdentifier = new TestIdentifier(o.getInitialTestCase() == null ? 0 : 1);
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
			return; //TODO throw an exception?
		}
		
		//splits items in sublists having same target method
		final Map<String, List<JBSEResult>> splitItems = 
				items.stream().collect(Collectors.groupingBy(r -> r.getTargetClassName() + ":" + r.getTargetMethodDescriptor() + ":" + r.getTargetMethodName()));

		//launches an EvoSuite process for each sublist
		final ArrayList<Thread> threadsEvosuiteEnd = new ArrayList<>();
		int testCountStart = testCountInitial;
		for (List<JBSEResult> subItems : splitItems.values()) {
			final int testCount = testCountStart; //copy into final variable to keep compiler happy
			testCountStart += subItems.size(); //for the next iteration
			
			//generates and compiles the wrappers
			emitAndCompileEvoSuiteWrappers(testCount, subItems);
			
			//builds the EvoSuite command line
			final List<String> evosuiteCommand = buildEvoSuiteCommand(testCount, subItems); 

			//launches EvoSuite
			final Path evosuiteLogFilePath = Paths.get(this.tmpPath + "/evosuite-log-" + testCount + ".txt");
			final Process processEvosuite;
			try {
				processEvosuite = launchEvoSuite(testCount, evosuiteCommand, evosuiteLogFilePath);
			} catch (IOException e) {
				System.out.println("[EVOSUITE] Unexpected I/O error while running EvoSuite: " + e.getMessage());
				return; //TODO throw an exception?
			}

			//launches a thread that waits for tests and schedules 
			//JBSE for exploring them
			final Thread tJBSE = new Thread(() -> detectTestsAndScheduleJBSE(testCount, subItems, evosuiteLogFilePath));
			tJBSE.start();
			
			//launches another thread that waits for EvoSuite to end
			//and then kills the previous thread
			final Thread tEvosuiteEnd = new Thread(() -> {
				try {
					processEvosuite.waitFor();
				} catch (InterruptedException e) {
					//this should never happen
					System.out.println("[EVOSUITE] Unexpected interruption of EvoSuite: " + e.getMessage());
				}
				tJBSE.interrupt();
			});
			tEvosuiteEnd.start();
			threadsEvosuiteEnd.add(tEvosuiteEnd);
		}
		
		//waits for all the Evosuite processes to end (if it didn't the performer
		//would consider the job over and would incorrectly detect whether it is idle)
		for (Thread tEvosuiteEnd : threadsEvosuiteEnd) {
			try {
				tEvosuiteEnd.join();
			} catch (InterruptedException e) {
				//nothing to do
			}
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
	 *        All the items in {@code items} must refer to the same target method, i.e., must have same
	 *        {@link JBSEResult#getTargetClassName() class name}, {@link JBSEResult#getTargetMethodDescriptor() method descriptor}, and 
	 *        {@link JBSEResult#getTargetMethodName() method name}.
	 * @return a command line in the format of a {@link List}{@code <}{@link String}{@code >},
	 *         suitable to be passed to a {@link ProcessBuilder}.
	 */
	private List<String> buildEvoSuiteCommand(int testCountInitial, List<JBSEResult> items) {
		final String targetClass = items.get(0).getTargetClassName();
		final String targetMethodDescriptor = items.get(0).getTargetMethodDescriptor();
		final String targetMethodName = items.get(0).getTargetMethodName();
		final String classpathEvosuite = this.binPath + File.pathSeparator + this.sushiLibPath + File.pathSeparator + this.tmpPath;
		final List<String> retVal = new ArrayList<String>();
		retVal.add("java");
		retVal.add("-Xmx4G");
		retVal.add("-jar");
		retVal.add(this.evosuitePath);
		retVal.add("-class");
		retVal.add(targetClass.replace('/', '.'));
		retVal.add("-mem");
		retVal.add("2048");
		retVal.add("-DCP=" + classpathEvosuite); 
		retVal.add("-Dassertions=false");
		retVal.add("-Dglobal_timeout=" + this.timeBudgetSeconds);
		retVal.add("-Dreport_dir=" + this.tmpPath);
		retVal.add("-Dsearch_budget=" + this.timeBudgetSeconds);
		retVal.add("-Dtest_dir=" + outPath);
		retVal.add("-Dvirtual_fs=false");
		retVal.add("-Dselection_function=ROULETTEWHEEL");
		retVal.add("-Dcriterion=PATHCONDITION");		
		retVal.add("-Dsushi_statistics=true");
		retVal.add("-Dinline=false");
		retVal.add("-Dsushi_modifiers_local_search=true");
		retVal.add("-Dpath_condition_target=LAST_ONLY");
		retVal.add("-Duse_minimizer_during_crossover=true");
		retVal.add("-Davoid_replicas_of_individuals=true"); 
		retVal.add("-Dno_change_iterations_before_reset=30");
		if (this.useMOSA) {
			retVal.add("-Demit_tests_incrementally=true");
			retVal.add("-Dcrossover_function=SUSHI_HYBRID");
			retVal.add("-Dalgorithm=DYNAMOSA");
			retVal.add("-generateMOSuite");
		} else {
			retVal.add("-Djunit_suffix=" + "_" + testCountInitial  + "_Test");
			retVal.add("-Dhtml=false");
			retVal.add("-Dcrossover_function=SINGLEPOINT");
			retVal.add("-Dcrossover_implementation=SUSHI_HYBRID");
			retVal.add("-Dmax_size=1");
			retVal.add("-Dmax_initial_tests=1");
		}
		final StringBuilder optionPC = new StringBuilder("-Dpath_condition=");
		for (int i = testCountInitial; i < testCountInitial + items.size(); ++i) {
			if (i > testCountInitial) {
				optionPC.append(":");
			}
			optionPC.append(targetClass.replace('/', '.') + "," + targetMethodName + targetMethodDescriptor + ",EvoSuiteWrapper_" + i);
		}
		retVal.add(optionPC.toString());
		
		return retVal;
	}
	
	private Process launchEvoSuite(int testCountInitial, List<String> evosuiteCommand, Path evosuiteLogFilePath) throws IOException {
		final ProcessBuilder pb = new ProcessBuilder(evosuiteCommand).redirectErrorStream(true).redirectOutput(evosuiteLogFilePath.toFile());
		final Process pr = pb.start();
		return pr;
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
	 * Waits for EvoSuite to emit test classes and schedules JBSE
	 * for their further analysis.
	 * 
	 * @param testCountInitial an {@code int}, the number used to identify 
	 *        the generated tests. The test generated from {@code items.get(i)}
	 *        will be numbered {@code testCountInitial + i}.
	 * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}, results of symbolic execution.
	 * @param evosuiteLogFilePath the {@link Path} of the EvoSuite log file.
	 */
	private void detectTestsAndScheduleJBSE(int testCountInitial, List<JBSEResult> items, Path evosuiteLogFilePath) {
		final HashSet<Integer> generated = new HashSet<>();
		try (final BufferedReader r = Files.newBufferedReader(evosuiteLogFilePath)) {
			//modified from https://stackoverflow.com/a/154588/450589
			boolean ended = false;
			while (true) {
				final String line = r.readLine();
				if (line == null) { 
					//no lines in the file
					if (ended) {
						//no more lines in the future: warns about tests that 
						//have not been generated and exits
						int testCount = testCountInitial;
						for (JBSEResult item : items) {
							if (!generated.contains(testCount)) {
								System.out.println("[EVOSUITE] Failed to generate a test case for PC: " + item.getFinalState().getPathCondition());
							}
							++testCount;
						}
						return;
					} else {
						//possibly more lines in the future: wait a little bit
						//and retry
						try {
							Thread.sleep(2000);
						} catch (InterruptedException ex) {
							//evosuite has ended
							ended = true;
						}
					}
				} else {
					//read a line: check if it reports the emission of a test case
					//and in the positive case schedule JBSE to analyze it
					final Pattern patternEmittedTest = Pattern.compile("^.*\\* EMITTED TEST CASE: EvoSuiteWrapper_(\\d+), \\w+\\z");
					final Matcher matcherEmittedTest = patternEmittedTest.matcher(line);
					if (matcherEmittedTest.matches()) {
						final int testCount = Integer.parseInt(matcherEmittedTest.group(1));
						generated.add(testCount);
						final JBSEResult item = items.get(testCount - testCountInitial);
						checkTestCompileAndScheduleJBSE(testCount, item);
					}
				}
			}
		} catch (IOException e) {
			System.out.println("[EVOSUITE] Unexpected I/O error while reading EvoSuite log file " + evosuiteLogFilePath.toString() + ": " + e.getMessage());
			//TODO throw an exception?
		}
	}
		
	private void checkTestCompileAndScheduleJBSE(int testCount, JBSEResult item) {
		final State finalState = item.getFinalState();
		final int depth = item.getDepth();
		
		//checks if EvoSuite generated the files
		final String testCaseClassName = item.getTargetClassName() + "_" + testCount + "_Test";
		final String testCaseScaff = this.outPath + "/" + testCaseClassName + "_scaffolding.java";
		final String testCase = this.outPath + "/" + testCaseClassName + ".java";
		if (!new File(testCase).exists() || !new File(testCaseScaff).exists()) {
			System.out.println("Failed to generate the test case " + testCaseClassName + " for PC: " + finalState.getPathCondition() + ": the generated files do not seem to exist");
			return;
		}
		
		//compiles the generated test
		final String classpathCompilationTest = this.binPath + File.pathSeparator + this.sushiLibPath + File.pathSeparator + this.evosuitePath;
		final Path javacLogFilePath = Paths.get(this.tmpPath + "/javac-log-test-" +  testCount + ".txt");
		final String[] javacParametersTestScaff = { "-cp", classpathCompilationTest, "-d", this.binPath, testCaseScaff }; //TODO change destination to temp directory
		final String[] javacParametersTestCase = { "-cp", classpathCompilationTest, "-d", this.binPath, testCase }; //TODO change destination to temp directory
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
			this.getOutputBuffer().add(new EvosuiteResult(item, newTC, depth + 1));
		} catch (NoSuchMethodException e) { 
			//EvoSuite failed to generate the test case, thus we just ignore it 
			System.out.println("[EVOSUITE] Failed to generate the test case " + testCaseClassName + " for PC: " + finalState.getPathCondition() + ": the generated file does not contain a test method");
		}
	}
}
