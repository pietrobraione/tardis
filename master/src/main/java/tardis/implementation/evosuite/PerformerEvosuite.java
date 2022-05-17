package tardis.implementation.evosuite;

import static jbse.bc.ClassLoaders.CLASSLOADER_APP;
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
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import shaded.org.evosuite.ga.FitnessFunction;
import shaded.org.evosuite.rmi.UtilsRMI;
import shaded.org.evosuite.rmi.service.EvosuiteRemote;
import shaded.org.evosuite.rmi.service.TestListenerRemote;
import shaded.org.evosuite.utils.Randomness;
import sushi.formatters.StateFormatterSushiPathCondition;
import tardis.Options;
import tardis.framework.OutputBuffer;
import tardis.framework.Performer;
import tardis.implementation.common.NoJavaCompilerException;
import tardis.implementation.data.JBSEResultInputOutputBuffer;
import tardis.implementation.jbse.JBSEResult;

/**
 * A {@link Performer} that consumes {@link JBSEResult}s by invoking Evosuite
 * to build tests from path conditions. Upon success the produced tests are 
 * emitted as {@link EvosuiteResult}s.
 * 
 * @author Pietro Braione
 */
public final class PerformerEvosuite extends Performer<JBSEResult, EvosuiteResult> implements TestListenerRemote {
    private static final Logger LOGGER = LogManager.getFormatterLogger(PerformerEvosuite.class);
    
    private final List<List<String>> visibleTargetMethods;
    private final JavaCompiler compiler;
    private final JBSEResultInputOutputBuffer in;
    private final Options o;
    private final long timeBudgetSeconds;
    private final String classpathEvosuite;
    private final URL[] classpathTestURLClassLoader;
    private final String classpathCompilationTest;
    private final String classpathCompilationWrapper;
    public static final String appRmiIdentifier = "RmiEvoSuite";
    private int testCount;
    private volatile boolean stopForSeeding;
    private int registryPort = -1;
	private Registry registry;
	EvosuiteRemote evosuiteMasterNode = null;
	private final Process evosuiteProcess = null;
    
    public PerformerEvosuite(Options o, JBSEResultInputOutputBuffer in, OutputBuffer<EvosuiteResult> out) 
    throws Exception {
        super(in, out, o.getNumOfThreadsEvosuite(), o.getNumMOSATargets(), o.getThrottleFactorEvosuite(), o.getTimeoutMOSATaskCreationDuration(), o.getTimeoutMOSATaskCreationUnit());
        this.visibleTargetMethods = getTargets(o);
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new NoJavaCompilerException();
        }
        this.in = in;
        this.o = o;
        this.timeBudgetSeconds = o.getEvosuiteTimeBudgetUnit().toSeconds(o.getEvosuiteTimeBudgetDuration());
        final String classesPathString = String.join(File.pathSeparator, stream(o.getClassesPath()).map(Object::toString).toArray(String[]::new)); 
        this.classpathEvosuite = classesPathString + File.pathSeparator + this.o.getJBSELibraryPath().toString() + File.pathSeparator + this.o.getSushiLibPath().toString();
        final ArrayList<Path> classpathTestPath = new ArrayList<>(o.getClassesPath());
        classpathTestPath.add(this.o.getSushiLibPath());
        classpathTestPath.add(this.o.getTmpBinDirectoryPath());
        classpathTestPath.add(this.o.getEvosuitePath());
        try {
            this.classpathTestURLClassLoader = stream(classpathTestPath).map(PerformerEvosuite::toURL).toArray(URL[]::new);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MalformedURLException) {
                throw (MalformedURLException) e.getCause();
            } else {
                throw e;
            }
        }
        this.classpathCompilationTest = this.o.getTmpBinDirectoryPath().toString() + File.pathSeparator + classesPathString + File.pathSeparator + this.o.getJBSELibraryPath().toString() + File.pathSeparator + this.o.getSushiLibPath().toString() + File.pathSeparator + this.o.getEvosuitePath().toString();
        this.classpathCompilationWrapper = classesPathString + File.pathSeparator + this.o.getSushiLibPath().toString();
        this.testCount = (o.getInitialTestCase() == null ? 0 : 1);
        this.stopForSeeding = false;
        
        // Create registry for RMI communication
        createAndConnectRegistry();
    }

	private void createAndConnectRegistry() throws Exception {
		int port = 2000;
		port += Randomness.nextInt(20000);

		final int TRIES = 100;
		for (int i = 0; i < TRIES; i++) {
			try {
				int candidatePort = port + i;
				UtilsRMI.ensureRegistryOnLoopbackAddress();
				registry = LocateRegistry.createRegistry(candidatePort);
				registryPort = candidatePort;
			} catch (RemoteException e) {
			}
		}
		if (registry == null) {
			throw new RuntimeException("unable to create registry");
		}
		System.out.println("Started registry on port " + registryPort);
		
		TestListenerRemote stub = (TestListenerRemote) UtilsRMI.exportObject(this);
		registry.rebind(appRmiIdentifier, stub);
		System.out.println("Connected to registry " + registryPort);
	}
    
    private static URL toURL(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            LOGGER.error("Internal error while converting path %s to URL", path.toString());
            throw new RuntimeException(e);
        }                   
    }

    @Override
    protected Runnable makeJob(List<JBSEResult> items) {
        while (this.stopForSeeding) ; //ugly spinlocking
        final int testCountInitial = this.testCount;
        final boolean isSeed = items.stream().map(JBSEResult::isSeed).reduce(true, (a, b) -> a && b); 
        if (isSeed) {
            this.stopForSeeding = true;
        } else {
            this.testCount += items.size();
        }
        
        final Runnable job = (isSeed ? 
                              () -> generateTestsAndScheduleJBSESeed(testCountInitial, items) :
                              () -> generateTestsAndScheduleJBSE(testCountInitial, items));
        return job;
    }

    /**
     * Invokes EvoSuite to generate a set of {@link TestCase}s that cover a 
     * set of methods. Used only during the seeding phase.
     * 
     * @param testCountInitial an {@code int}, the number used to identify 
     *        the generated tests. The test generated will be numbered starting 
     *        from {@code testCountInitial} henceforth.
     * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}. It must be
     *        {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true}
     *        for all {@code item} in {@code items}, and 
     *        {@code item1.}{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == item2.}{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code ()}
     *        for all {@code item1}, {@code item2} in {@code items}. Moreover, if 
     *        {@code items.}{@link List#get(int) get}{@code (0).}{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == false},
     *        then it must be {@code items.}{@link List#size() size}{@code () == 1}.
     */
    private void generateTestsAndScheduleJBSESeed(int testCountInitial, List<JBSEResult> items) {
        try {
            final boolean targetIsASetOfMethods = items.get(0).hasTargetMethod();
            if (targetIsASetOfMethods) {
            	generateTestsAndScheduleJBSESeedTargetIsASetOfMethods(testCountInitial, items);
            } else {
            	generateTestsAndScheduleJBSESeedTargetIsAClass(testCountInitial, items);
            }
        } finally {
            //unlocks
            this.stopForSeeding = false;
        }
    }

    /**
     * Invokes EvoSuite to generate a set of {@link TestCase}s that cover a 
     * set of methods. Used only during the seeding phase, handles the case
     * where the target methods are indicated singularly.
     * 
     * @param testCountInitial an {@code int}, the number used to identify 
     *        the generated tests. The test generated will be numbered starting 
     *        from {@code testCountInitial} henceforth.
     * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}. It must be
     *        {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true}
     *        for all {@code item} in {@code items}, and 
     *        {@code item.}{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == true}
     *        for all {@code item} in {@code items}.
     */
    private void generateTestsAndScheduleJBSESeedTargetIsASetOfMethods(int testCountInitial, List<JBSEResult> items) {
    	//updates testCount
    	this.testCount += items.size();

    	//builds the EvoSuite wrappers
    	int testCount = testCountInitial;
    	for (JBSEResult item : items) {
    		try {
    			emitAndCompileEvoSuiteWrapperSeedTargetMethod(testCount++, item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName());
    		} catch (CompilationFailedWrapperException e) {
    			LOGGER.error("Internal error: EvoSuite wrapper %s compilation failed", e.file.toAbsolutePath().toString());
    			return;
    		} catch (IOFileCreationException e) { 
    			LOGGER.error("Unexpected I/O error during EvoSuite wrapper creation/compilation while creating file %s", e.file.toAbsolutePath().toString());
    			LOGGER.error("Message: %s", e.toString());
    			LOGGER.error("Stack trace:");
    			for (StackTraceElement elem : e.getStackTrace()) {
    				LOGGER.error("%s", elem.toString());
    			}
    			return;
    		} catch (IOException e) { 
    			LOGGER.error("Unexpected I/O error while creating EvoSuite seed wrapper");
    			LOGGER.error("Message: %s", e.toString());
    			LOGGER.error("Stack trace:");
    			for (StackTraceElement elem : e.getStackTrace()) {
    				LOGGER.error("%s", elem.toString());
    			}
    			return;
    		} catch (InvalidClassFileFactoryClassException | InvalidInputException | ClassFileNotFoundException | ClassFileIllFormedException | 
    		ClassFileNotAccessibleException | IncompatibleClassFileException | PleaseLoadClassException | BadClassFileVersionException | 
    		WrongClassNameException | CannotAssumeSymbolicObjectException | MethodNotFoundException | MethodCodeNotFoundException | 
    		HeapMemoryExhaustedException | RenameUnsupportedException e) {
    			LOGGER.error("Internal error while creating EvoSuite seed wrapper");
    			LOGGER.error("Message: %s", e.toString());
    			LOGGER.error("Stack trace:");
    			for (StackTraceElement elem : e.getStackTrace()) {
    				LOGGER.error("%s", elem.toString());
    			}
    			return;
    		}
    	}

    	//builds the EvoSuite command line
    	final List<String> evosuiteCommand = buildEvoSuiteCommand(testCountInitial, items);

    	//launches EvoSuite
//    	final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-seed.txt");
//    	final Process evosuiteProcess;
//    	try {
//    		evosuiteProcess = launchProcess(evosuiteCommand);
//    		LOGGER.info("Launched EvoSuite seed process, command line: %s", evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
//    	} catch (IOException e) {
//    		LOGGER.error("Unexpected I/O error while running EvoSuite seed process");
//    		LOGGER.error("Message: %s", e.toString());
//    		LOGGER.error("Stack trace:");
//    		for (StackTraceElement elem : e.getStackTrace()) {
//    			LOGGER.error("%s", elem.toString());
//    		}
//    		return;
//    	}
    	
    	final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-seed.txt");
    	
    	// Launch EvoSuite
    	if (evosuiteProcess == null) {
    		launchEvosuite(evosuiteCommand, evosuiteLogFilePath);
    	} else {
    		// TODO: update EvoSuite instance
    	}
    			

    	//launches a thread that waits for tests and schedules 
    	//JBSE for exploring them
    	final Thread tJBSE;
    	try {
    		final TestDetector tdJBSE = new TestDetector(this, this.o, testCountInitial, items, evosuiteProcess.getInputStream(), evosuiteLogFilePath, this.in);
    		tJBSE = new Thread(tdJBSE);
    		tJBSE.start();
    	} catch (IOException e) {
    		LOGGER.error("Unexpected I/O error while opening the EvoSuite output file");
    		LOGGER.error("Message: %s", e.toString());
    		LOGGER.error("Stack trace:");
    		for (StackTraceElement elem : e.getStackTrace()) {
    			LOGGER.error("%s", elem.toString());
    		}
    		evosuiteProcess.destroy();
    		return;
    	}

        //waits for the thread to end (if it didn't the performer
        //would consider the job over and would incorrectly detect 
    	//whether it is idle)
    	boolean interrupted = false;
    	try {
    		tJBSE.join();
    	} catch (InterruptedException e) {
    		interrupted = true;
    	}

        //same for the EvoSuite process (safety net)
    	try {
    		if (interrupted) {
    			evosuiteProcess.destroy();
    		} else {
    			evosuiteProcess.waitFor();
    		}
    	} catch (InterruptedException e) {
    		evosuiteProcess.destroy();
    	}
    }
    

    /**
     * Invokes EvoSuite to generate a set of {@link TestCase}s that cover a 
     * set of methods. Used only during the seeding phase, handles the case
     * where the target methods are indicated implicitly by specifying a 
     * target class.
     * 
     * @param testCountInitial an {@code int}, the number used to identify 
     *        the generated tests. The test generated will be numbered starting 
     *        from {@code testCountInitial} henceforth.
     * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}. It must be
     *        {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true}
     *        for all {@code item} in {@code items}, {@code items.}{@link List#size() size}{@code () == 1} and 
     *        {@code items.}{@link List#get(int) get}{@code (0).}{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == false}.
     *        for all {@code item} in {@code items}.
     */
    private void generateTestsAndScheduleJBSESeedTargetIsAClass(int testCountInitial, List<JBSEResult> items) {
    	//builds the EvoSuite command line
    	final List<String> evosuiteCommand = buildEvoSuiteCommandSeedTargetClass(items.get(0));

    	//launches EvoSuite
    	final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-seed.txt");
//    	final Process evosuiteProcess;
//    	try {
//    		evosuiteProcess = launchProcess(evosuiteCommand, evosuiteLogFilePath);
//    		LOGGER.info("Launched EvoSuite seed process, command line: %s", evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
//    	} catch (IOException e) {
//    		LOGGER.error("Unexpected I/O error while running EvoSuite seed process");
//    		LOGGER.error("Message: %s", e.toString());
//    		LOGGER.error("Stack trace:");
//    		for (StackTraceElement elem : e.getStackTrace()) {
//    			LOGGER.error("%s", elem.toString());
//    		}
//    		return;
//    	}
    	
    	// Launch EvoSuite
    	if (evosuiteProcess == null) {
    		launchEvosuite(evosuiteCommand, evosuiteLogFilePath);
    	} else {
    		// TODO: update EvoSuite instance
    	}

    	//waits for EvoSuite to end
    	try {
    		evosuiteProcess.waitFor();
    	} catch (InterruptedException e) {
    		//this performer was shut down: kills the EvoSuite jobs
    		//and return
    		evosuiteProcess.destroy();
    		return;
    	}

    	//splits output
    	final List<JBSEResult> splitItems;
    	try {
    		final SeedSplitter seedSplitter = new SeedSplitter(this.o, items.get(0).getTargetClassName(), this.visibleTargetMethods, testCountInitial);
    		splitItems = seedSplitter.split();
    	} catch (NoTestFileException e) {
    		LOGGER.error("Failed to split the seed test case %s: the generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString());
    		return;
    	} catch (NoTestFileScaffoldingException e) {
    		LOGGER.error("Failed to split the seed test case %s: the generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString());
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

    	//schedules JBSE
    	int testCount = testCountInitial;
    	for (JBSEResult item : splitItems) {
    		try {
    			checkTestCompileAndScheduleJBSE(testCount, item);
    		} catch (NoTestFileException e) {
    			LOGGER.error("Failed to generate the test case %s for post-frontier path condition %s:%s: The generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
    			//continue
    		} catch (NoTestFileScaffoldingException e) {
    			LOGGER.error("Failed to generate the test case %s for post-frontier path condition %s:%s: The generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
    			//continue
    		} catch (NoTestMethodException e) {
    			LOGGER.warn("Failed to generate the test case %s for post-frontier path condition %s:%s: The generated files does not contain a test method (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
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
    		} finally {
    			++testCount;
    		}

    		//updates the counter
    		this.testCount = testCount;
    	}
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
        //splits items in sublists having same target method
        final List<List<JBSEResult>> splitItems = new ArrayList<>();
        for (int i = 0; i < items.size() / this.o.getNumMOSATargets(); ++i) {
        	final int start = i * items.size();
        	final int end = (i + 1) * items.size();
        	splitItems.add(items.subList(start, end));
        }
        if (items.size() % this.o.getNumMOSATargets() != 0) {
        	splitItems.add(items.subList((items.size() / this.o.getNumMOSATargets()) * items.size(), items.size()));
        }

        //launches an EvoSuite process for each sublist
        final ArrayList<TestDetector> testDetectors = new ArrayList<>();
        final ArrayList<Thread> threads = new ArrayList<>();
        final ArrayList<Process> processes = new ArrayList<>();
        int testCountStart = testCountInitial;
        for (List<JBSEResult> subItems : splitItems) {
            final int testCount = testCountStart; //copy into final variable to keep compiler happy
            testCountStart += subItems.size(); //for the next iteration

            //generates and compiles the wrappers
            final ArrayList<JBSEResult> compiled = new ArrayList<>();
            int i = testCount;
            for (JBSEResult item : subItems) {
                try {
                    emitAndCompileEvoSuiteWrapper(i, item.getInitialState(), item.getPostFrontierState(), item.getStringLiterals(), item.getStringOthers(), item.getForbiddenExpansions());
                    compiled.add(item);
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
                } catch (FrozenStateException e) {
                    LOGGER.error("Internal error while creating EvoSuite wrapper");
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    //falls through
                }
                ++i;
            }

            //skips to next subitems if failed to compile all
            if (compiled.size() == 0) {
            	continue;
            }
            
            //builds the EvoSuite command line
            final List<String> evosuiteCommand = buildEvoSuiteCommand(testCount, compiled); 

//            //launches EvoSuite
            final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-" + testCount + ".txt");
//            final Process evosuiteProcess;
//            try {
//                evosuiteProcess = launchProcess(evosuiteCommand);
//                processes.add(evosuiteProcess);
//                LOGGER.info("Launched EvoSuite process, command line: %s", evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
//            } catch (IOException e) {
//                LOGGER.error("Unexpected I/O error while running EvoSuite process");
//                LOGGER.error("Message: %s", e.toString());
//                LOGGER.error("Stack trace:");
//                for (StackTraceElement elem : e.getStackTrace()) {
//                    LOGGER.error("%s", elem.toString());
//                }
//                continue;
//            }
            
         // Launch EvoSuite
        	if (evosuiteProcess == null) {
        		launchEvosuite(evosuiteCommand, evosuiteLogFilePath);
        	} else {
        		// TODO: update EvoSuite instance
        	}

            //launches a thread that waits for tests and schedules 
            //JBSE for exploring them
            try {
            	final TestDetector tdJBSE = new TestDetector(this, this.o, testCount, subItems, evosuiteProcess.getInputStream(), evosuiteLogFilePath, this.in);
                final Thread tJBSE = new Thread(tdJBSE);
                tJBSE.start();
                testDetectors.add(tdJBSE);
                threads.add(tJBSE);
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error while opening the EvoSuite output file");
                LOGGER.error("Message: %s", e.toString());
                LOGGER.error("Stack trace:");
                for (StackTraceElement elem : e.getStackTrace()) {
                    LOGGER.error("%s", elem.toString());
                }
                evosuiteProcess.destroy();
            }
        }

        //waits for all the threads to end (if it didn't the performer
        //would consider the job over and would incorrectly detect whether 
        //it is idle)
        boolean interrupted = false;
        for (Thread thread : threads) {
            try {
                if (interrupted) {
                    thread.interrupt();
                } else {
                    thread.join();
                }
            } catch (InterruptedException e) {
                interrupted = true;
                thread.interrupt();
            }
        }
        
        //same for processes (safety net)
        for (Process process : processes) {
            try {
                if (interrupted) {
                    process.destroy();
                } else {
                    process.waitFor();
                }
            } catch (InterruptedException e) {
                interrupted = true;
                process.destroy();
            }
        }
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
     * @throws IOException
     * @throws InvalidClassFileFactoryClassException
     * @throws InvalidInputException
     * @throws ClassFileNotFoundException
     * @throws ClassFileIllFormedException
     * @throws ClassFileNotAccessibleException
     * @throws IncompatibleClassFileException
     * @throws PleaseLoadClassException
     * @throws BadClassFileVersionException
     * @throws RenameUnsupportedException
     * @throws WrongClassNameException
     * @throws CannotAssumeSymbolicObjectException
     * @throws MethodNotFoundException
     * @throws MethodCodeNotFoundException
     * @throws HeapMemoryExhaustedException
     * @throws IOFileCreationException
     * @throws CompilationFailedWrapperException
     */
    private void emitAndCompileEvoSuiteWrapperSeedTargetMethod(int testCount, String targetMethodClassName, String targetMethodDescriptor, String targetMethodName) 
    throws IOException, InvalidClassFileFactoryClassException, InvalidInputException, ClassFileNotFoundException, 
    ClassFileIllFormedException, ClassFileNotAccessibleException, IncompatibleClassFileException, 
    PleaseLoadClassException, BadClassFileVersionException, RenameUnsupportedException, WrongClassNameException, 
    CannotAssumeSymbolicObjectException, MethodNotFoundException, MethodCodeNotFoundException, HeapMemoryExhaustedException, 
    IOFileCreationException, CompilationFailedWrapperException {
        final Classpath cp = new Classpath(this.o.getJBSELibraryPath(),
                                           Paths.get(System.getProperty("java.home", "")), 
                                           new ArrayList<>(Arrays.stream(System.getProperty("java.ext.dirs", "").split(File.pathSeparator))
                                           .map(s -> Paths.get(s)).collect(Collectors.toList())), 
                                           this.o.getClassesPath());
        final State initialState = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, cp, ClassFileFactoryJavassist.class, new HashMap<>(), new HashMap<>(), new SymbolFactory());
        final ClassFile cf = initialState.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, targetMethodClassName, true);
        initialState.pushFrameSymbolic(cf, new Signature(targetMethodClassName, targetMethodDescriptor, targetMethodName));
        final State finalState = initialState.clone();
        final Map<Long, String> stringLiterals = Collections.emptyMap();
        final Set<Long> stringOthers = Collections.emptySet();
        emitAndCompileEvoSuiteWrapper(testCount, initialState, finalState, stringLiterals, stringOthers, null);
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
     * @throws FrozenStateException if {@code initialState} is frozen.
     * @throws IOFileCreationException if some I/O error occurs while creating the wrapper, the directory 
     *         that must contain it, or the compilation log file.
     * @throws CompilationFailedWrapperException if the compilation of the wrapper class fails.
     */
    private void emitAndCompileEvoSuiteWrapper(int testCount, State initialState, State finalState, Map<Long, String> stringLiterals, Set<Long> stringOthers, Set<String> forbiddenExpansions) 
    throws FrozenStateException, IOFileCreationException, CompilationFailedWrapperException {
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
     * Builds the command line for invoking EvoSuite for the generation of the
     * tests (common part).
     * 
     * @param targetClass a {@code String}, the name of the target class.
     * @return a command line in the format of an {@link ArrayList}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     */
    private ArrayList<String> buildEvoSuiteCommandCommon(String targetClass) {
        final ArrayList<String> retVal = new ArrayList<>();
        retVal.add(this.o.getJava8Command());
        retVal.add("-Xmx4G");
        retVal.add("-jar");
        retVal.add(this.o.getEvosuitePath().toString());
        retVal.add("-class");
        retVal.add(targetClass);
        retVal.add("-mem");
        retVal.add("2048");
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
        
        retVal.add("-Dexternal_rmi_registry_port=" + registryPort);
        retVal.add("-Dtest_listener_rmi_identifier=" + appRmiIdentifier);
        retVal.add("-Dinjected_path_conditions_checking_rate=50");
        retVal.add("-Ddismiss_path_conditions_no_improve_iterations=50");
        
        return retVal;
    }
    
    /**
     * Builds the command line for invoking EvoSuite for the generation of the
     * seed tests with a class as target.
     * 
     * @param item a {@link JBSEResult} such that {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true && }{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == false}.
     *        All the items in {@code items} must refer to the same target method, i.e., must have same
     *        {@link JBSEResult#getTargetMethodClassName() class name}, {@link JBSEResult#getTargetMethodDescriptor() method descriptor}, and 
     *        {@link JBSEResult#getTargetMethodName() method name}.
     * @return a command line in the format of an {@link ArrayList}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     */
    private ArrayList<String> buildEvoSuiteCommandSeedTargetClass(JBSEResult item) {
        final boolean isTargetAMethod = (item.getTargetClassName() == null);
        final String targetClass = (isTargetAMethod ? item.getTargetMethodClassName() : item.getTargetClassName()).replace('/', '.');
        final ArrayList<String> retVal = buildEvoSuiteCommandCommon(targetClass);
        retVal.add("-Dcriterion=BRANCH");
        retVal.add("-Djunit_suffix=" + "_Seed_Test");
        return retVal;
    }

    /**
     * Builds the command line for invoking EvoSuite.
     * 
     * @param testCountInitial an {@code int}, the number used to identify 
     *        the generated tests. The test generated from {@code items.get(i)}
     *        will be numbered {@code testCountInitial + i}.
     * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}, results of symbolic execution.
     *        All the items in {@code items} must refer to the same target class, i.e., must have same
     *        {@link JBSEResult#getTargetMethodClassName() class name}.
     * @return a command line in the format of an {@link ArrayList}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     */
    private ArrayList<String> buildEvoSuiteCommand(int testCountInitial, List<JBSEResult> items) {
    	final JBSEResult item = items.get(0);
        final String targetClass = item.getTargetMethodClassName().replace('/', '.');
        final ArrayList<String> retVal = buildEvoSuiteCommandCommon(targetClass);
        retVal.add("-Dcriterion=PATHCONDITION");             
        retVal.add("-Dsushi_statistics=true");
        retVal.add("-Dpath_condition_target=LAST_ONLY");
        retVal.add("-Dpath_condition_evaluators_dir=" + this.o.getTmpBinDirectoryPath().toString());
        retVal.add("-Demit_tests_incrementally=true");
        final StringBuilder optionPC = new StringBuilder("-Dpath_condition=");
        for (int i = testCountInitial; i < testCountInitial + items.size(); ++i) {
            if (i > testCountInitial) {
                optionPC.append(":");
            }
            final int itemNumber = i - testCountInitial;
            final String targetMethodDescriptor = items.get(itemNumber).getTargetMethodDescriptor();
            final String targetMethodName = items.get(itemNumber).getTargetMethodName();
            final String targetPackage = targetClass.substring(0, targetClass.lastIndexOf('.'));
            optionPC.append(targetClass + "," + targetMethodName + targetMethodDescriptor + "," + targetPackage + ".EvoSuiteWrapper_" + i);
        }
        retVal.add(optionPC.toString());
        return retVal;
    }

    /**
     * Creates and launches an external process. The stderr of the
     * process will be redirected to the stdout.
     * 
     * @param commandLine a {@link List}{@code <}{@link String}{@code >}, the command line
     *        to launch the process in the format expected by {@link ProcessBuilder}.
     * @return the created {@link Process}.
     * @throws IOException if thrown by {@link ProcessBuilder#start()}.
     */
    private Process launchProcess(List<String> commandLine) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(commandLine).redirectErrorStream(true);
        final Process pr = pb.start();
        return pr;
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
    private Process launchProcess(List<String> commandLine, Path logFilePath) throws IOException {
        final ProcessBuilder pb = new ProcessBuilder(commandLine).redirectErrorStream(true).redirectOutput(logFilePath.toFile());
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
    void checkTestCompileAndScheduleJBSE(int testCount, JBSEResult item) 
    throws NoTestFileException, NoTestFileScaffoldingException, NoTestMethodException, IOFileCreationException, 
    CompilationFailedTestException, CompilationFailedTestScaffoldingException, ClassFileAccessException {
        //checks if EvoSuite generated the files
        final String testCaseClassName = (item.hasTargetMethod() ? item.getTargetMethodClassName() : item.getTargetClassName()) + "_" + testCount + "_Test";
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
    
    private void launchEvosuite(final List<String> evosuiteCommand, Path evosuiteLogFilePath) {
		Thread t = new Thread() {
			@Override
			public void run() {
				try {
					final ProcessBuilder pb = new ProcessBuilder(evosuiteCommand).redirectErrorStream(true).redirectOutput(evosuiteLogFilePath.toFile());
					final Process processEvosuite = pb.start();
					System.out.println("Launched EvoSuite process, command line: " + evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
					try {
						processEvosuite.waitFor();
					} catch (InterruptedException e) {
						//the performer was shut down: kill the EvoSuite job
						System.err.println("Unexpected InterruptedException while running EvoSuite: " + e);
						processEvosuite.destroy();
					}
				} catch (IOException e) {
					System.err.println("Unexpected I/O error while running EvoSuite: " + e);
					throw new RuntimeException(e);
				}
				System.out.println("EvoSuite process finished");
				System.exit(0);
			}
		};
		t.start();
	}

    @Override
	public void evosuiteServerReady(String evosuiteServerRmiIdentifier) throws RemoteException {
		System.out.println("Evosuite server is ready, name is " + evosuiteServerRmiIdentifier);
		try {
			evosuiteMasterNode = (EvosuiteRemote) registry.lookup(evosuiteServerRmiIdentifier);
		} catch (NotBoundException e) {
			System.err.println("Error when connecting to evosuite server via rmi with identifier " + evosuiteServerRmiIdentifier + ": " + e);
			e.printStackTrace();
		}
		System.out.println("Connected to EvoSuite process with RMI identifier: " + evosuiteServerRmiIdentifier);
	}

	@Override
	public void generatedTest(FitnessFunction<?> goal, String testFileName) throws RemoteException {
		System.out.println("Evosuite server communicated new test: " + testFileName + " -- It is for goal: " + goal);
	}

	@Override
	public void dismissedFitnessGoal(FitnessFunction<?> goal, int iteration, double fitnessValue, int[] updateIterations) throws RemoteException {
		System.out.println("Evosuite server communicated dismissed goal: " + goal + ", iteration is " + iteration + ", fitness is " + fitnessValue + ", with updates at iterations " + Arrays.toString(updateIterations));
	}
}
