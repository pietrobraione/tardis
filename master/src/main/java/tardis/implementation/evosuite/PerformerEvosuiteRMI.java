package tardis.implementation.evosuite;

import static jbse.bc.ClassLoaders.CLASSLOADER_APP;
import static shaded.org.evosuite.rmi.UtilsRMI.exportObject;
import static tardis.implementation.common.Util.getTargets;
import static tardis.implementation.common.Util.stream;
import static tardis.implementation.common.Util.stringifyPostFrontierPathCondition;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jbse.bc.ClassFile;
import jbse.bc.ClassFileFactoryJavassist;
import jbse.bc.Classpath;
import jbse.bc.Signature;
import jbse.bc.exc.BadClassFileVersionException;
import jbse.bc.exc.ClassFileIllFormedException;
import jbse.bc.exc.ClassFileNotAccessibleException;
import jbse.bc.exc.ClassFileNotFoundException;
import jbse.bc.exc.IncompatibleClassFileException;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.bc.exc.MethodCodeNotFoundException;
import jbse.bc.exc.MethodNotFoundException;
import jbse.bc.exc.PleaseLoadClassException;
import jbse.bc.exc.RenameUnsupportedException;
import jbse.bc.exc.WrongClassNameException;
import jbse.common.exc.InvalidInputException;
import jbse.mem.State;
import jbse.mem.exc.CannotAssumeSymbolicObjectException;
import jbse.mem.exc.FrozenStateException;
import jbse.mem.exc.HeapMemoryExhaustedException;
import jbse.val.HistoryPoint;
import jbse.val.SymbolFactory;
import shaded.org.evosuite.coverage.branch.BranchCoverageTestFitness;
import shaded.org.evosuite.ga.FitnessFunction;
import shaded.org.evosuite.rmi.service.EvosuiteRemote;
import shaded.org.evosuite.rmi.service.TestListenerRemote;
import sushi.formatters.StateFormatterSushiPathCondition;
import tardis.Options;
import tardis.framework.OutputBuffer;
import tardis.framework.Performer;
import tardis.framework.PerformerMultiServer;
import tardis.implementation.common.NoJavaCompilerException;
import tardis.implementation.data.JBSEResultInputOutputBuffer;
import tardis.implementation.jbse.JBSEResult;

/**
 * A {@link Performer} that consumes {@link JBSEResult}s by invoking Evosuite
 * to build tests from path conditions. Upon success the produced tests are 
 * emitted as {@link EvosuiteResult}s.
 * 
 * @author Pietro Braione
 * @author Lorenzo Benatti
 */
public final class PerformerEvosuiteRMI extends PerformerMultiServer<JBSEResult, EvosuiteResult> implements TestListenerRemote {
    private static final Logger LOGGER = LogManager.getFormatterLogger(PerformerEvosuite.class);
    private static final String TARDIS_RMI_IDENTIFIER = "TARDIS_RMI_IDENTIFIER";
    private static final int RMI_REGISTRY_PORT_BASE = 2000;
    private static final int RMI_REGISTRY_PORT_RANGE = 20000;
    
    private final JavaCompiler compiler;
    private final Options o;
	private final List<List<String>> visibleTargetMethods;
    private final long timeBudgetSeconds;
    private final String classpathEvosuite;
    private final URL[] classpathTestURLClassLoader;
    private final String classpathCompilationTest;
    private final String classpathCompilationWrapper;
    private final AtomicInteger testCount = new AtomicInteger(0);
	private final Map<Integer, JBSEResult> itemsMap = Collections.synchronizedMap(new HashMap<>());
	private final ArrayList<Process> evosuiteProcesses = new ArrayList<>();
	private final Map<String, EvosuiteRemote> evosuiteNodes = Collections.synchronizedMap(new HashMap<>());
	private final Map<String, Integer> evosuiteCapacityCounter = Collections.synchronizedMap(new HashMap<>());
	private Registry registry = null;
    private int registryPort = -1;
    private boolean terminated = false;
    private volatile boolean stopUntilFirstEvosuite = true;
    
    public PerformerEvosuiteRMI(Options o, JBSEResultInputOutputBuffer in, OutputBuffer<EvosuiteResult> out) 
    throws NoJavaCompilerException, ClassNotFoundException, MalformedURLException, SecurityException, 
    RemoteException, InterruptedException {
        super("PerformerEvosuiteRMI", in, out, o.getNumOfThreadsEvosuite(), o.getNumEvosuiteJobsOverloaded(), o.getNumTargetsEvosuiteJob(), o.getThrottleFactorEvosuite(), o.getTimeoutEvosuiteJobCreationDuration() / o.getNumTargetsEvosuiteJob(), o.getTimeoutEvosuiteJobCreationUnit());
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new NoJavaCompilerException();
        }
        this.o = o;
        this.visibleTargetMethods = getTargets(o);
        this.timeBudgetSeconds = o.getEvosuiteTimeBudgetUnit().toSeconds(o.getEvosuiteTimeBudgetDuration());
        final String classesPathString = String.join(File.pathSeparator, stream(o.getClassesPath()).map(Object::toString).toArray(String[]::new)); 
        this.classpathEvosuite = classesPathString + File.pathSeparator + this.o.getJBSELibraryPath().toString() + File.pathSeparator + this.o.getSushiLibPath().toString();
        final ArrayList<Path> classpathTestPath = new ArrayList<>(o.getClassesPath());
        classpathTestPath.add(this.o.getSushiLibPath());
        classpathTestPath.add(this.o.getTmpBinDirectoryPath());
        classpathTestPath.add(this.o.getEvosuitePath());
        try {
            this.classpathTestURLClassLoader = stream(classpathTestPath).map(PerformerEvosuiteRMI::toURL).toArray(URL[]::new);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MalformedURLException) {
                throw (MalformedURLException) e.getCause();
            } else {
                throw e;
            }
        }
        this.classpathCompilationTest = this.o.getTmpBinDirectoryPath().toString() + File.pathSeparator + classesPathString + File.pathSeparator + this.o.getJBSELibraryPath().toString() + File.pathSeparator + this.o.getSushiLibPath().toString() + File.pathSeparator + this.o.getEvosuitePath().toString();
        this.classpathCompilationWrapper = classesPathString + File.pathSeparator + this.o.getSushiLibPath().toString();
        
        //creates the RMI registry, exports this object and binds it to the registry
        startRMI();
        
        //creates the EvoSuite instances
        createEvosuite();
    }
    
    private static URL toURL(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            LOGGER.error("Internal error while converting path %s to URL", path.toString());
            throw new RuntimeException(e);
        }                   
    }

	private void startRMI() throws RemoteException {
		final int port = RMI_REGISTRY_PORT_BASE + ThreadLocalRandom.current().nextInt(RMI_REGISTRY_PORT_RANGE);
		final int attempts = 100;
		RemoteException exc = null;
		for (int i = 0; i < attempts; ++i) {
			try {
				final int candidatePort = port + i;
				this.registry = LocateRegistry.createRegistry(candidatePort);
				this.registryPort = candidatePort;
			} catch (RemoteException e) {
				exc = e;
				//continue
			}
		}
		if (this.registry == null) {
			throw exc;
		}
		LOGGER.info("Started RMI registry on port %d", this.registryPort);
		
		final TestListenerRemote stub = (TestListenerRemote) exportObject(this);
		this.registry.rebind(TARDIS_RMI_IDENTIFIER, stub);
		LOGGER.info("PerformerEvosuiteRMI exported and bound to RMI registry");
	}
	
	private void createEvosuite() throws ClassNotFoundException, MalformedURLException, SecurityException, InterruptedException {
        final List<String> evosuiteCommand = buildEvoSuiteCommand(); 
        for (int i = 0; i < this.o.getNumOfThreadsEvosuite(); ++i) {
            final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-" + i + ".txt");
        	launchEvosuite(evosuiteCommand, evosuiteLogFilePath);
        }
        
        //waits for at least one EvoSuite instance to connect back
        //(ugly spinlock)
        while (this.stopUntilFirstEvosuite) ;
	}
    
    /**
     * Builds the command line for invoking EvoSuite.
     * 
     * @return a command line in the format of an {@link ArrayList}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     * @throws SecurityException 
     * @throws MalformedURLException 
     * @throws ClassNotFoundException 
     */
    private ArrayList<String> buildEvoSuiteCommand() throws ClassNotFoundException, MalformedURLException, SecurityException {
        final List<String> targetMethod = this.o.getTargetMethod();
        final String targetClass = this.o.getTargetClass();
        final ArrayList<String> retVal = new ArrayList<>();
        retVal.add(this.o.getJava8Command());
        retVal.add("-Xmx4G");
        retVal.add("-jar");
        retVal.add(this.o.getEvosuitePath().toString());
    	retVal.add("-class");
        if (targetClass == null) {
        	retVal.add(targetMethod.get(0).replace('/', '.'));
        } else {
        	retVal.add(targetClass.replace('/', '.'));
        }
        retVal.add("-mem");
        retVal.add("2048");
        /*if (targetClass != null && this.o.getVisibility() == Visibility.PUBLIC) {
        	final StringBuilder optionMethod = new StringBuilder();
        	optionMethod.append("-Dtarget_method_list=\"");
        	boolean firstDone = false;
        	for (List<String> target: this.visibleTargetMethods) {
        		if (firstDone) {
        			optionMethod.append(':');
        		} else {
        			firstDone = true;
        		}
        		optionMethod.append(target.get(2));
        		optionMethod.append(target.get(1).replace("$", "\\$"));
        	}
        	optionMethod.append('"');
        	retVal.add(optionMethod.toString());
        	retVal.add("-criterion");
        	retVal.add("method");
        } else if (targetMethod != null) {
        	final StringBuilder optionMethod = new StringBuilder();
        	optionMethod.append("-Dtarget_method_list=\"");
        	optionMethod.append(targetMethod.get(2));
        	optionMethod.append(targetMethod.get(1).replace("$", "\\$"));
        	optionMethod.append('"');
        	retVal.add(optionMethod.toString());
        	retVal.add("-criterion");
        	retVal.add("method");
        }*/
        retVal.add("-Dmock_if_no_generator=false");
        retVal.add("-Dreplace_system_in=false");
        retVal.add("-Dreplace_gui=false");
        retVal.add("-Dp_functional_mocking=0.0");
        retVal.add("-DCP=" + this.classpathEvosuite); 
        retVal.add("-Dassertions=false");
        retVal.add("-Dreport_dir=" + this.o.getTmpDirectoryPath().toString());
        retVal.add("-Dsearch_budget=" + this.timeBudgetSeconds);
        retVal.add("-Dtest_dir=" + this.o.getTmpTestsDirectoryPath().toString());
        retVal.add("-Dvirtual_fs=false");
        retVal.add("-Dselection_function=ROULETTEWHEEL");
        retVal.add("-Dinline=false");
        retVal.add("-Dsushi_modifiers_local_search=true");
        retVal.add("-Duse_minimizer_during_crossover=true");
        retVal.add("-Davoid_replicas_of_individuals=true"); 
        retVal.add("-Dno_change_iterations_before_reset=30");
        if (this.o.getEvosuiteNoDependency()) {
            retVal.add("-Dno_runtime_dependency");
        }
        retVal.add("-Dmax_subclasses_per_class=1");
        retVal.add("-Dcrossover_function=SUSHI_HYBRID");
        retVal.add("-Dalgorithm=DYNAMOSA");
        retVal.add("-generateMOSuite");
        retVal.add("-Dexternal_rmi_registry_port=" + this.registryPort);
        retVal.add("-Dtest_listener_rmi_identifier=" + TARDIS_RMI_IDENTIFIER);
        retVal.add("-Dinjected_path_conditions_checking_rate=50");
        retVal.add("-Ddismiss_path_conditions_no_improve_iterations=50");
        retVal.add("-Dcriterion=PATHCONDITION:BRANCH");             
        retVal.add("-Dsushi_statistics=true");
        retVal.add("-Dpath_condition_target=LAST_ONLY");
        retVal.add("-Dpath_condition_evaluators_dir=" + this.o.getTmpBinDirectoryPath().toString());
        retVal.add("-Demit_tests_incrementally=true");
        return retVal;
    }
    
    private void launchEvosuite(final List<String> evosuiteCommand, Path evosuiteLogFilePath) {
        try {
            this.evosuiteProcesses.add(launchProcess(evosuiteCommand, evosuiteLogFilePath));
            LOGGER.info("Launched EvoSuite process, command line: %s", evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
        } catch (IOException e) {
            LOGGER.error("Unexpected I/O error while running EvoSuite process");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
        }
	}

    /**
     * Creates and launches an external process.
     * 
     * @param commandLine a {@link List}{@code <}{@link String}{@code >}, the command line
     *        to launch the process in the format expected by {@link ProcessBuilder}.
     * @param logFilePath a {@link Path} to a log file where stdout and stderr of the
     *        process will be redirected.
     * @return the created {@link Process}.
     * @throws IOException if thrown by {@link ProcessBuilder#start()}.
     */
    private static Process launchProcess(List<String> commandLine, Path logFilePath) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(commandLine).redirectErrorStream(true).redirectOutput(logFilePath.toFile());
        final Process pr = pb.start();
        return pr;
    }

    @Override
    protected Runnable makeJob(List<JBSEResult> items) {
        final Runnable job = this.terminated ? (() -> { }) : (() -> generateWrappersAndSendGoalsToEvosuite(items));
    	if (this.terminated) {
    		LOGGER.info("All Evosuite instances terminated, Evosuite job ignored");
    	}
        return job;
    }
    
    @Override
    protected void onStop() {
		for (Process process : this.evosuiteProcesses) {
			try {
				process.waitFor();
			} catch (InterruptedException e) {
				//nevermind and continue
			}
		}
		try {
			this.registry.unbind(TARDIS_RMI_IDENTIFIER);
		} catch (RemoteException | NotBoundException e) {
			//just ignore
		}
		try {
			UnicastRemoteObject.unexportObject(this, false);
		} catch (NoSuchObjectException e) {
			//just ignore
		}
    }
    
    @Override
    protected synchronized int availableWorkers() {
    	int retVal = 0;
    	for (Map.Entry<String, Integer> e : this.evosuiteCapacityCounter.entrySet()) {
    		retVal += e.getValue();
    	}
    	return retVal;
    }

    @Override
	public synchronized void evosuiteServerReady(String evosuiteServerRmiIdentifier) throws RemoteException {
		LOGGER.info("Evosuite server is ready, RMI identifier is %s", evosuiteServerRmiIdentifier);
		try {
			final EvosuiteRemote remote = (EvosuiteRemote) this.registry.lookup(evosuiteServerRmiIdentifier);
			this.evosuiteNodes.put(evosuiteServerRmiIdentifier, remote);
			this.evosuiteCapacityCounter.put(evosuiteServerRmiIdentifier, Integer.valueOf(this.o.getNumEvosuiteJobsOverloaded()));
			
			//at least one EvoSuite server is ready: unlock spinlock
			this.stopUntilFirstEvosuite = false;
		} catch (NotBoundException e) {
			LOGGER.error("Failed connection to Evosuite server with RMI identifier %s", evosuiteServerRmiIdentifier);
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
		}
		LOGGER.info("Connected to Evosuite process with RMI identifier %s", evosuiteServerRmiIdentifier);
	}
    
	@Override
	public synchronized void generatedTest(String evosuiteServerRmiIdentifier, FitnessFunction<?> goal, String testFileName) throws RemoteException {
		LOGGER.info("Evosuite server communicated new test %s for goal %s", testFileName, goal);
		final int old = this.evosuiteCapacityCounter.get(evosuiteServerRmiIdentifier);  
		this.evosuiteCapacityCounter.put(evosuiteServerRmiIdentifier, Integer.valueOf(old + 1));
		
    	final List<Pair<JBSEResult, Integer>> items;
		if (goal instanceof BranchCoverageTestFitness) {
			//the goal is a branch, i.e., no path condition:
	    	//splits the test
    		final String targetClass = (this.o.getTargetClass() == null ? this.o.getTargetMethod().get(0) : this.o.getTargetClass());
    		final EvosuiteSplitter splitter = new EvosuiteSplitter(this.o, testFileName, targetClass, this.visibleTargetMethods, this.testCount);
	    	try {
	    		items = splitter.split();
	    	} catch (NoTestFileException e) {
	    		LOGGER.error("Failed to split the test case %s: the generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString());
	    		return;
	    	} catch (NoTestFileScaffoldingException e) {
	    		LOGGER.error("Failed to split the test case %s: the generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString());
	    		return;
	    	} catch (IOFileCreationException e) { 
	    		LOGGER.error("Unexpected I/O error during EvoSuite seed splitting while creating file %s", e.file.toAbsolutePath().toString());
	    		LOGGER.error("Message: %s", e.toString());
	    		LOGGER.error("Stack trace:");
	    		for (StackTraceElement elem : e.getStackTrace()) {
	    			LOGGER.error("%s", elem.toString());
	    		}
	    		return;
	    	} catch (IOException e) {
	    		LOGGER.error("Unexpected I/O error during EvoSuite seed splitting while invoking Javaparser");
	    		LOGGER.error("Message: %s", e.toString());
	    		LOGGER.error("Stack trace:");
	    		for (StackTraceElement elem : e.getStackTrace()) {
	    			LOGGER.error("%s", elem.toString());
	    		}
	    		return;
	    	}
		} else {
			//the goal is a path condition
			final String[] testFileNameSplit = testFileName.split("_");
			final int testCount = Integer.parseInt(testFileNameSplit[testFileNameSplit.length - 2]);
			final JBSEResult jbseResult = this.itemsMap.get(testCount);
			items = Collections.singletonList(new Pair<>(jbseResult, testCount));
		}

    	//schedules JBSE
    	for (Pair<JBSEResult, Integer> item : items) {
    		try {
    			checkTestCompileAndScheduleJBSE(item.second(), item.first());
    		} catch (NoTestFileException e) {
    			LOGGER.error("Failed to generate the test case %s for post-frontier path condition %s:%s: the generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
    			//continue
    		} catch (NoTestFileScaffoldingException e) {
    			LOGGER.error("Failed to generate the test case %s for post-frontier path condition %s:%s: the generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
    			//continue
    		} catch (NoTestMethodException e) {
    			LOGGER.warn("Failed to generate the test case %s for post-frontier path condition %s:%s: the generated files does not contain a test method (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
    			//continue
    		} catch (CompilationFailedTestException e) {
    			LOGGER.error("Internal error: EvoSuite test case %s compilation failed", e.file.toAbsolutePath().toString());
    			//continue
    		} catch (CompilationFailedTestScaffoldingException e) {
    			LOGGER.error("Internal error: EvoSuite test case scaffolding %s compilation failed", e.file.toAbsolutePath().toString());
    			//continue
    		} catch (ClassFileAccessException e) {
    			LOGGER.error("Unexpected error while verifying that class %s exists and has a test method", e.className);
    			LOGGER.error("Message: %s", e.e.toString());
    			LOGGER.error("Stack trace:");
    			for (StackTraceElement elem : e.e.getStackTrace()) {
    				LOGGER.error("%s", elem.toString());
    			}
    			//continue
    		} catch (IOFileCreationException e) {
    			LOGGER.error("Unexpected I/O error while creating test case compilation log file %s", e.file.toAbsolutePath().toString());
    			LOGGER.error("Message: %s", e.e.toString());
    			LOGGER.error("Stack trace:");
    			for (StackTraceElement elem : e.e.getStackTrace()) {
    				LOGGER.error("%s", elem.toString());
    			}
    			//continue
    		}
    	}
	}

	@Override
	public synchronized void dismissedFitnessGoal(String evosuiteServerRmiIdentifier, FitnessFunction<?> goal, int iteration, double fitnessValue, int[] updateIterations) throws RemoteException {
		LOGGER.info("Evosuite server %s communicated dismissed goal %s, iteration is %d, fitness is %f, with updates at iterations %s", evosuiteServerRmiIdentifier, goal, iteration, fitnessValue, Arrays.toString(updateIterations));
		final int old = this.evosuiteCapacityCounter.get(evosuiteServerRmiIdentifier);  
		this.evosuiteCapacityCounter.put(evosuiteServerRmiIdentifier, Integer.valueOf(old + 1));
	}
	

	@Override
	public synchronized void evosuiteServerShutdown(String evosuiteServerRmiIdentifier) throws RemoteException {
		LOGGER.info("Evosuite server %s communicated shutdown", evosuiteServerRmiIdentifier);
		this.evosuiteNodes.remove(evosuiteServerRmiIdentifier);
		this.evosuiteCapacityCounter.put(evosuiteServerRmiIdentifier, Integer.valueOf(this.o.getNumEvosuiteJobsOverloaded()));
		if (this.evosuiteNodes.isEmpty()) {
			LOGGER.info("All Evosuite servers down");
			this.terminated = true;
		}
	}

    /**
     * Checks whether EvoSuite emitted a well-formed test class, and in the
     * positive case compiles the generated test and schedules JBSE for its
     * exploration.
     *  
     * @param testCount an {@code int}, the number that identifies 
     *        the generated test.
     * @param item a {@link JBSEResult}, the result of the symbolic execution
     *        from which the test was generated.
     * @throws NoTestFileException if the test file does not exist.
     * @throws NoTestFileScaffoldingException if the scaffolding file does not exist. 
     * @throws NoTestMethodException if the test method does not exist in the test class.
     * @throws IOFileCreationException if some I/O error occurs while creating the compilation log file.
     * @throws CompilationFailedTestException if the compilation of the test class fails.
     * @throws CompilationFailedTestScaffoldingException if the compilation of the scaffolding class fails.
     * @throws ClassFileAccessException if the test class is not accessible.
     */
    private void checkTestCompileAndScheduleJBSE(int testCount, JBSEResult item) 
    throws NoTestFileException, NoTestFileScaffoldingException, NoTestMethodException, IOFileCreationException, 
    CompilationFailedTestException, CompilationFailedTestScaffoldingException, ClassFileAccessException {
        //checks if EvoSuite generated the files
        final String testCaseClassName = item.getTargetMethodClassName() + "_" + testCount + "_Test";
        final Path testCaseScaff = (this.o.getEvosuiteNoDependency() ? null : this.o.getTmpTestsDirectoryPath().resolve(testCaseClassName + "_scaffolding.java"));
        final Path testCase = this.o.getTmpTestsDirectoryPath().resolve(testCaseClassName + ".java");
        if (!testCase.toFile().exists()) {
            throw new NoTestFileException(testCase);
        }
        if (testCaseScaff != null && !testCaseScaff.toFile().exists()) {
            throw new NoTestFileScaffoldingException(testCaseScaff);
        }

        //compiles the generated test
        final Path javacLogFilePath = this.o.getTmpTestsDirectoryPath().resolve("javac-log-test-" +  testCount + ".txt");
        final String[] javacParametersTestCase = { "-cp", this.classpathCompilationTest, "-d", this.o.getTmpBinDirectoryPath().toString(), testCase.toString() };
        try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
            if (testCaseScaff != null) {
                final String[] javacParametersTestScaff = { "-cp", this.classpathCompilationTest, "-d", this.o.getTmpBinDirectoryPath().toString(), testCaseScaff.toString() };
                final int successTestCaseScaff = this.compiler.run(null, w, w, javacParametersTestScaff);
                if (successTestCaseScaff != 0) {
                    throw new CompilationFailedTestScaffoldingException(testCaseScaff);
                }
            }
            final int successTestCase = this.compiler.run(null, w, w, javacParametersTestCase);
            if (successTestCase != 0) {
                throw new CompilationFailedTestException(testCase);
            }
        } catch (IOException e) {
            throw new IOFileCreationException(e, javacLogFilePath);
        }

        //creates the TestCase and schedules it for further exploration
        try {
            checkTestExists(testCaseClassName);
            final int depth = item.getDepth();
            LOGGER.info("Generated test case %s, depth: %d, post-frontier path condition: %s:%s", testCaseClassName, depth, item.getTargetMethodSignature(), stringifyPostFrontierPathCondition(item));
            final TestCase newTestCase = new TestCase(testCaseClassName, "()V", "test0", this.o.getTmpTestsDirectoryPath(), (testCaseScaff != null));
            getOutputBuffer().add(new EvosuiteResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), item.getPathConditionGenerated(), newTestCase, depth + 1));
        } catch (NoSuchMethodException e) { 
            throw new NoTestMethodException(testCase, item.getTargetMethodSignature(), stringifyPostFrontierPathCondition(item));
        } catch (SecurityException | NoClassDefFoundError | ClassNotFoundException e) {
            throw new ClassFileAccessException(e, testCaseClassName);
        }
    }

    /**
     * Checks that an emitted test class has the {@code test0} method,
     * to filter out the cases where EvoSuite fails but emits the test class.
     * 
     * @param className a {@link String}, the name of the test class.
     * @throws NoSuchMethodException if the class {@code className} has not
     *         a {@code void test0()} method.
     * @throws SecurityException if the method {@code test0} of class 
     *         {@code className} cannot be accessed. 
     * @throws NoClassDefFoundError if class {@code className} does not exist.
     * @throws ClassNotFoundException if class {@code className} does not exist.
     */
    private void checkTestExists(String className) 
    throws NoSuchMethodException, SecurityException, NoClassDefFoundError, ClassNotFoundException {
        final URLClassLoader cloader = URLClassLoader.newInstance(this.classpathTestURLClassLoader); 
        cloader.loadClass(className.replace('/',  '.')).getDeclaredMethod("test0");
    }
    
    /**
     * Invokes EvoSuite to generate a set of {@link TestCase}s that cover a 
     * set of path condition, and then explores the generated test cases 
     * starting from the depth of the respective path conditions.
     * 
     * @param items a a {@link List}{@code <}{@link JBSEResult}{@code >},  
     *        where each {@link JBSEResult} is the result of symbolic execution.
     */
    private void generateWrappersAndSendGoalsToEvosuite(List<JBSEResult> items) {
    	//generates and compiles the wrappers
    	final ArrayList<Pair<JBSEResult, Integer>> compiled = new ArrayList<>();
        for (JBSEResult item : items) {
            final int testCount = this.testCount.getAndIncrement();
            try {
            	//emits and compiles the wrapper
            	if (item.isSeed()) {
            		emitAndCompileEvoSuiteWrapperSeed(testCount, item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName());
            	} else {
            		emitAndCompileEvoSuiteWrapper(testCount, item.getInitialState(), item.getPostFrontierState(), item.getStringLiterals(), item.getStringOthers(), item.getForbiddenExpansions());
            	}
            	
            	//if emitting the wrapper had success, adds the item to
            	//the list of the compiled items
                compiled.add(new Pair<>(item, testCount));
                this.itemsMap.put(testCount, item);
            } catch (CompilationFailedWrapperException e) {
                LOGGER.error("Internal error: EvoSuite wrapper %s compilation failed", e.file.toAbsolutePath().toString());
                //falls through
            } catch (IOFileCreationException e) {
                LOGGER.error("Unexpected I/O error during EvoSuite wrapper creation/compilation while creating file %s", e.file.toAbsolutePath().toString());
                LOGGER.error("Message: %s", e.toString());
                LOGGER.error("Stack trace:");
                for (StackTraceElement elem : e.getStackTrace()) {
                    LOGGER.error("%s", elem.toString());
                }
                //falls through
            } catch (UnexpectedJBSELibFailureException e) {
                LOGGER.error("Internal error while creating EvoSuite wrapper");
                LOGGER.error("Message: %s", e.e.toString());
                LOGGER.error("Stack trace:");
                for (StackTraceElement elem : e.e.getStackTrace()) {
                    LOGGER.error("%s", elem.toString());
                }
                //falls through
            }
        }
        
        //sends goals to EvoSuite
        sendGoalsToSomeEvosuite(compiled);
    }

    /**
     * Emits and compiles the EvoSuite wrapper for the path condition of some state
     * (only for seed tests, and in the case the target is a method).
     * 
     * @param testCount an {@code int}, the number used to identify the test.
     * @param targetMethodClassName a {@link String}, the name of the class
     *        of the target method.
     * @param targetMethodDescriptor a {@link String}, the descriptor
     *        of the target method.
     * @param targetMethodName a {@link String}, the name of 
     *        the target method.
     * @throws IOFileCreationException if some I/O error occurs while creating the wrapper, the directory 
     *         that must contain it, or the compilation log file.
     * @throws CompilationFailedWrapperException if the compilation of the wrapper class fails.
     * @throws UnexpectedJBSELibFailureException if some exception was raised while using the
     *         JBSE library to create the wrapper.
     */
    private void emitAndCompileEvoSuiteWrapperSeed(int testCount, String targetMethodClassName, String targetMethodDescriptor, String targetMethodName) 
    throws IOFileCreationException, CompilationFailedWrapperException, UnexpectedJBSELibFailureException {
    	try {
    	//makes a wrapper for the "true" path condition corresponding to the 
    	//entry point of the method
        final Classpath cp = new Classpath(this.o.getJBSELibraryPath(),
                                           Paths.get(System.getProperty("java.home", "")), 
                                           new ArrayList<>(Arrays.stream(System.getProperty("java.ext.dirs", "").split(File.pathSeparator))
                                           .map(s -> Paths.get(s)).collect(Collectors.toList())), 
                                           this.o.getClassesPath());
        final State initialState = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, cp, ClassFileFactoryJavassist.class, 
                                             new HashMap<>(), new HashMap<>(), new SymbolFactory());
        final ClassFile cf = initialState.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, targetMethodClassName, true);
        initialState.pushFrameSymbolic(cf, new Signature(targetMethodClassName, targetMethodDescriptor, targetMethodName));
        final State finalState = initialState.clone();
        final Map<Long, String> stringLiterals = Collections.emptyMap();
        final Set<Long> stringOthers = Collections.emptySet();
        emitAndCompileEvoSuiteWrapper(testCount, initialState, finalState, stringLiterals, stringOthers, null);
    	} catch (IOException | InvalidClassFileFactoryClassException | InvalidInputException | ClassFileNotFoundException |
    	        ClassFileIllFormedException | ClassFileNotAccessibleException | IncompatibleClassFileException |
    	        PleaseLoadClassException | BadClassFileVersionException | RenameUnsupportedException | WrongClassNameException |
    	        CannotAssumeSymbolicObjectException | MethodNotFoundException | MethodCodeNotFoundException | HeapMemoryExhaustedException e) {
    		throw new UnexpectedJBSELibFailureException(e);
    	}
    }
    
    /**
     * Emits and compiles the EvoSuite wrapper for the path condition of some state.
     * 
     * @param testCount an {@code int}, the number used to identify the test.
     * @param initialState a {@link State}; must be the initial state in the execution 
     *        for which we want to generate the wrapper.
     * @param finalState a {@link State}; must be the final state in the execution 
     *        for which we want to generate the wrapper.
     * @param stringLiterals a {@link Map}{@code <}{@link Long}{@code , }{@link String}{@code >}, 
     *        mapping a heap position of a {@link String} literal to the
     *        corresponding value of the literal.
     * @param stringOthers a {@link List}{@code <}{@link Long}{@code >}, 
     *        listing the heap positions of the nonconstant {@link String}s.
     * @throws IOFileCreationException if some I/O error occurs while creating the wrapper, the directory 
     *         that must contain it, or the compilation log file.
     * @throws CompilationFailedWrapperException if the compilation of the wrapper class fails.
     * @throws UnexpectedJBSELibFailureException if some exception was raised while using the
     *         JBSE library to create the wrapper.
     */
    private void emitAndCompileEvoSuiteWrapper(int testCount, State initialState, State finalState, Map<Long, String> stringLiterals, Set<Long> stringOthers, Set<String> forbiddenExpansions) 
    throws IOFileCreationException, CompilationFailedWrapperException, UnexpectedJBSELibFailureException {
        final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount, () -> initialState, true);
        fmt.setStringsConstant(stringLiterals);
        fmt.setStringsNonconstant(stringOthers);
        if (forbiddenExpansions != null) {
        	fmt.setForbiddenExpansions(forbiddenExpansions);
        }
        fmt.formatPrologue();
        fmt.formatState(finalState);
        fmt.formatEpilogue();
        
        final Path wrapperFilePath;
        try { 
            final String initialCurrentClassName = initialState.getStack().get(0).getMethodClass().getClassName();
            final int lastSlash = initialCurrentClassName.lastIndexOf('/');
            final String initialCurrentClassPackageName = (lastSlash == -1 ? "" : initialCurrentClassName.substring(0, lastSlash));
            final Path wrapperDirectoryPath = this.o.getTmpWrappersDirectoryPath().resolve(initialCurrentClassPackageName);
            try {
                Files.createDirectories(wrapperDirectoryPath);
            } catch (IOException e) {
                throw new IOFileCreationException(e, wrapperDirectoryPath);
            }
            wrapperFilePath = wrapperDirectoryPath.resolve("EvoSuiteWrapper_" + testCount + ".java");
            try (final BufferedWriter w = Files.newBufferedWriter(wrapperFilePath)) {
                w.write(fmt.emit());
            } catch (IOException e) {
                throw new IOFileCreationException(e, wrapperFilePath);
            }
        } catch (FrozenStateException e) {
        	throw new UnexpectedJBSELibFailureException(e);
        } finally {
            fmt.cleanup();
        }

        final Path javacLogFilePath = this.o.getTmpDirectoryPath().resolve("javac-log-wrapper-" + testCount + ".txt");
        final String[] javacParameters = { "-cp", this.classpathCompilationWrapper, "-d", this.o.getTmpBinDirectoryPath().toString(), "-source", "8", "-target", "8", wrapperFilePath.toString() };
        try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
            final int success = this.compiler.run(null, w, w, javacParameters);
            if (success != 0) {
                throw new CompilationFailedWrapperException(wrapperFilePath);
            }
        } catch (IOException e) {
            throw new IOFileCreationException(e, javacLogFilePath);
        }
    }
    
    /**
     * Selects an instance of EvoSuite and runs it on a set of {@link JBSEResult}s for which 
     * the wrappers are available. 
     * 
     * @param items a {@link List}{@code <}{@link Pair}{@code <}{@link JBSEResult}{@code , }{@link Integer}{@code >>}.
     *        where each {@link JBSEResult} is the result of symbolic execution and its paired {@link Integer} is the
     *        identifier of the corresponding test case (if it will be generated).
     */
    private synchronized void sendGoalsToSomeEvosuite(List<Pair<JBSEResult, Integer>> items) {
    	if (items.size() == 0) {
    		return;
    	}
    	    	
    	//looks for the first EvoSuite with capacity greater than zero,
    	//if it fails, then it detects the least overloaded EvoSuite
    	Map.Entry<String, Integer> eBest = null;
    	int eBestAvailability = 0;
    	for (Map.Entry<String, Integer> e : this.evosuiteCapacityCounter.entrySet()) {
    		if (this.evosuiteNodes.containsKey(e.getKey())) {
    			final int eAvailability = e.getValue();
    			if (eAvailability > 0) {
    				eBest = e;
    				eBestAvailability = eAvailability;
    				break;
    			} else if (eBest == null || eAvailability > eBest.getValue()) {
    				eBest = e;
    				eBestAvailability = eAvailability;
    			}
    		}
    	}
    	
    	//all failed: sends to the less overloaded EvoSuite
    	if (eBest == null) {
    		LOGGER.error("Failed sending new goal to Evosuite: Failed search of Evosuite instance with best availability");
    		return;
    	}    	
		eBest.setValue(Integer.valueOf(eBestAvailability - 1));
    	sendGoalsToEvosuite(eBest.getKey(), items);
    }
    
    
    /**
     * Runs an instance of EvoSuite on a set of {@link JBSEResult}s for which 
     * the wrappers are available. 
     * 
     * @param evosuiteServerRmiIdentifier a {@link String}, the RMI identifier
     *        of the EvoSuite RMI server instance.
     * @param items a {@link List}{@code <}{@link Pair}{@code <}{@link JBSEResult}{@code , }{@link Integer}{@code >>}.
     *        where each {@link JBSEResult} is the result of symbolic execution and its paired {@link Integer} is the
     *        identifier of the corresponding test case (if it will be generated).
     */
    private void sendGoalsToEvosuite(String evosuiteServerRmiIdentifier, List<Pair<JBSEResult, Integer>> items) {
    	try {
    		final EvosuiteRemote evosuiteRemote = this.evosuiteNodes.get(evosuiteServerRmiIdentifier);
    		for (Pair<JBSEResult, Integer> item : items) {
        		final JBSEResult aJBSEResult = item.first();
        		final String targetClass = aJBSEResult.getTargetMethodClassName().replace('/', '.');
        		final String targetPackage = targetClass.substring(0, targetClass.lastIndexOf('.'));
    			final String targetMethodNameAndDescriptor = new StringBuilder(item.first().getTargetMethodName()).append(item.first().getTargetMethodDescriptor()).toString();
    			final String wrapperName = new StringBuilder(targetPackage).append(".EvoSuiteWrapper_").append(item.second()).toString();
    			evosuiteRemote.evosuite_injectFitnessFunction(targetClass, targetMethodNameAndDescriptor, wrapperName);
    			LOGGER.info("Sent new path conditions to Evosuite: %s in %s", targetMethodNameAndDescriptor, wrapperName);
    		}
    	} catch (RemoteException e) {
    		LOGGER.error("Failed sending new goal to Evosuite");
    		LOGGER.error("Message: %s", e.toString());
    		LOGGER.error("Stack trace:");
    		for (StackTraceElement elem : e.getStackTrace()) {
    			LOGGER.error("%s", elem.toString());
    		}
    	}
	}
}