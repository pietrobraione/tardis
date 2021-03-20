package tardis.implementation;

import static jbse.bc.ClassLoaders.CLASSLOADER_APP;
import static jbse.common.Type.splitParametersDescriptors;

import static tardis.implementation.Util.getInternalClassloader;
import static tardis.implementation.Util.getTargets;
import static tardis.implementation.Util.shorten;
import static tardis.implementation.Util.stream;
import static tardis.implementation.Util.stringifyPathCondition;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

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
import jbse.mem.Clause;
import jbse.mem.State;
import jbse.mem.exc.CannotAssumeSymbolicObjectException;
import jbse.mem.exc.FrozenStateException;
import jbse.mem.exc.HeapMemoryExhaustedException;
import jbse.val.HistoryPoint;
import jbse.val.SymbolFactory;
import sushi.formatters.StateFormatterSushiPathCondition;
import tardis.Options;
import tardis.framework.OutputBuffer;
import tardis.framework.Performer;

/**
 * A {@link Performer} that consumes {@link JBSEResult}s by invoking Evosuite
 * to build tests from path conditions. Upon success the produced tests are 
 * emitted as {@link EvosuiteResult}s.
 * 
 * @author Pietro Braione
 */
public final class PerformerEvosuite extends Performer<JBSEResult, EvosuiteResult> {
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
    private int testCount;
    private volatile boolean stopForSeeding;
    
    public PerformerEvosuite(Options o, JBSEResultInputOutputBuffer in, OutputBuffer<EvosuiteResult> out) 
    throws NoJavaCompilerException, ClassNotFoundException, MalformedURLException, SecurityException {
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
        final boolean isSeed = (items.size() == 1 && items.get(0).isSeed()); 
        if (isSeed) {
            this.stopForSeeding = true;
        } else {
            this.testCount += items.size();
        }
        final Runnable job = (isSeed ? 
                              () -> generateTestsAndScheduleJBSESeed(testCountInitial, items.get(0)) :
                              () -> generateTestsAndScheduleJBSE(testCountInitial, items));
        return job;
    }

    /**
     * Invokes EvoSuite to generate a set of {@link TestCase}s that cover a 
     * set of methods.
     * 
     * @param testCountInitial an {@code int}, the number used to identify 
     *        the generated tests. The test generated will be numbered starting 
     *        from {@code testCountInitial} henceforth.
     * @param item a {@link JBSEResult}. It must be
     *              {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true && item.}{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == false}.
     */
    private void generateTestsAndScheduleJBSESeed(int testCountInitial, JBSEResult item) {
        try {
            final boolean isTargetAMethod = (item.getTargetClassName() == null);

            if (isTargetAMethod) {
                //builds the EvoSuite wrapper
                try {
                    final Classpath cp = new Classpath(this.o.getJBSELibraryPath(),
                                                       Paths.get(System.getProperty("java.home", "")), 
                                                       new ArrayList<>(Arrays.stream(System.getProperty("java.ext.dirs", "").split(File.pathSeparator))
                                                       .map(s -> Paths.get(s)).collect(Collectors.toList())), 
                                                       this.o.getClassesPath());
                    final State initialState = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, cp, ClassFileFactoryJavassist.class, new HashMap<>(), new HashMap<>(), new SymbolFactory());
                    final ClassFile cf = initialState.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, item.getTargetMethodClassName(), true);
                    initialState.pushFrameSymbolic(cf, new Signature(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName()));
                    final State finalState = initialState.clone();
                    final Map<Long, String> stringLiterals = Collections.emptyMap();
                    final Set<Long> stringOthers = Collections.emptySet();
                    emitAndCompileEvoSuiteWrapper(testCountInitial, initialState, finalState, stringLiterals, stringOthers);
                } catch (CompilationFailedWrapperException e) {
                    LOGGER.error("Internal error: EvoSuite wrapper %s compilation failed", e.file.toAbsolutePath().toString());
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

                //builds the EvoSuite command line
                final List<String> evosuiteCommand = buildEvoSuiteCommand(testCountInitial, Collections.singletonList(item));

                //launches EvoSuite
                final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-" + testCountInitial + ".txt");
                final Process processEvosuite;
                try {
                    processEvosuite = launchProcess(evosuiteCommand, evosuiteLogFilePath);
                    LOGGER.info("Launched EvoSuite seed process, command line: %s", evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
                } catch (IOException e) {
                    LOGGER.error("Unexpected I/O error while running EvoSuite seed process");
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    return;
                }

                //waits for EvoSuite to end
                try {
                    processEvosuite.waitFor();
                } catch (InterruptedException e) {
                    //this performer was shut down: kills the EvoSuite job
                    //and return
                    processEvosuite.destroy();
                    return;
                }

                //schedules JBSE
                try {
                    checkTestCompileAndScheduleJBSE(testCountInitial, item);
                } catch (NoTestFileException e) {
                    LOGGER.error("Failed to generate the test case %s for path condition %s: the generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
                    return;
                } catch (NoTestFileScaffoldingException e) {
                    LOGGER.error("Failed to generate the test case %s for path condition %s: the generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
                    return;
                } catch (NoTestMethodException e) {
                    LOGGER.warn("Failed to generate the test case %s for path condition: %s: the generated files does not contain a test method (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
                    return;
                } catch (CompilationFailedTestException e) {
                    LOGGER.error("Internal error: EvoSuite test case %s compilation failed", e.file.toAbsolutePath().toString());
                    return;
                } catch (CompilationFailedTestScaffoldingException e) {
                    LOGGER.error("Internal error: EvoSuite test case scaffolding %s compilation failed", e.file.toAbsolutePath().toString());
                    return;
                } catch (ClassFileAccessException e) {
                    LOGGER.error("Unexpected error while verifying that class %s exists and has a test method", e.className);
                    LOGGER.error("Message: %s", e.e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    return;
                } catch (IOFileCreationException e) {
                    LOGGER.error("Unexpected I/O error while creating test case compilation log file %s", e.file.toAbsolutePath().toString());
                    LOGGER.error("Message: %s", e.e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    return;
                }

                //updates the counter
                this.testCount = testCountInitial + 1;
            } else {
                //builds the EvoSuite command line
                final List<String> evosuiteCommand = buildEvoSuiteCommandSeed(item); 

                //launches EvoSuite
                final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-seed.txt");
                final Process processEvosuite;
                try {
                    processEvosuite = launchProcess(evosuiteCommand, evosuiteLogFilePath);
                    LOGGER.info("Launched EvoSuite seed process, command line: %s", evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
                } catch (IOException e) {
                    LOGGER.error("Unexpected I/O error while running EvoSuite seed process");
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    return;
                }

                //waits for EvoSuite to end
                try {
                    processEvosuite.waitFor();
                } catch (InterruptedException e) {
                    //this performer was shut down: kills the EvoSuite job
                    //and return
                    processEvosuite.destroy();
                    return;
                }

                //splits output
                final List<JBSEResult> splitItems;
                try {
                    splitItems = splitEvosuiteSeed(testCountInitial, item);
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
                for (JBSEResult splitItem : splitItems) {
                    try {
                        checkTestCompileAndScheduleJBSE(testCount, splitItem);
                        ++testCount;
                    } catch (NoTestFileException e) {
                        LOGGER.error("Failed to generate the test case %s for path condition %s: the generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
                        //continue
                    } catch (NoTestFileScaffoldingException e) {
                        LOGGER.error("Failed to generate the test case %s for path condition %s: the generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
                        //continue
                    } catch (NoTestMethodException e) {
                        LOGGER.warn("Failed to generate the test case %s for path condition: %s: the generated files does not contain a test method (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
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

                //updates the counter
                this.testCount = testCount;
            }
        } finally {
            //unlocks
            this.stopForSeeding = false;
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
        final Map<String, List<JBSEResult>> splitItems = 
        items.stream().collect(Collectors.groupingBy(r -> r.getTargetMethodClassName() + ":" + r.getTargetMethodDescriptor() + ":" + r.getTargetMethodName()));

        //launches an EvoSuite process for each sublist
        final ArrayList<TestDetector> testDetectors = new ArrayList<>();
        final ArrayList<Thread> threads = new ArrayList<>();
        int testCountStart = testCountInitial;
        for (List<JBSEResult> subItems : splitItems.values()) {
            final int testCount = testCountStart; //copy into final variable to keep compiler happy
            testCountStart += subItems.size(); //for the next iteration

            //generates and compiles the wrappers
            final ArrayList<JBSEResult> compiled = new ArrayList<>();
            int i = testCount;
            for (JBSEResult item : subItems) {
                final State initialState = item.getInitialState();
                final State finalState = item.getFinalState();
                final Map<Long, String> stringLiterals = item.getStringLiterals();
                final Set<Long> stringOthers = item.getStringOthers();
                try {
                    emitAndCompileEvoSuiteWrapper(i, initialState, finalState, stringLiterals, stringOthers);
                    compiled.add(item);
                    //continue
                } catch (CompilationFailedWrapperException e) {
                    LOGGER.error("Internal error: EvoSuite wrapper %s compilation failed", e.file.toAbsolutePath().toString());
                    //continue
                } catch (IOFileCreationException e) {
                    LOGGER.error("Unexpected I/O error during EvoSuite wrapper creation/compilation while creating file %s", e.file.toAbsolutePath().toString());
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    //continue
                } catch (FrozenStateException e) {
                    LOGGER.error("Internal error while creating EvoSuite wrapper");
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    //continue
                }
                ++i;
            }

            //builds the EvoSuite command line
            final List<String> evosuiteCommand = buildEvoSuiteCommand(testCount, compiled); 

            //launches EvoSuite
            final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-" + testCount + ".txt");
            final Process evosuiteProcess;
            try {
                evosuiteProcess = launchProcess(evosuiteCommand);
                LOGGER.info("Launched EvoSuite process, command line: %s", evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error while running EvoSuite process");
                LOGGER.error("Message: %s", e.toString());
                LOGGER.error("Stack trace:");
                for (StackTraceElement elem : e.getStackTrace()) {
                    LOGGER.error("%s", elem.toString());
                }
                return;
            }

            //launches a thread that waits for tests and schedules 
            //JBSE for exploring them
            final TestDetector tdJBSE;
            try {
                tdJBSE = new TestDetector(testCount, subItems, evosuiteProcess.getInputStream(), evosuiteLogFilePath, this.in);
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
                return;
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
    }

    /**
     * Class for a {@link Runnable} that listens for the output produced by 
     * an instance of EvoSuite, and when this produces a test
     * schedules JBSE for its analysis.
     * 
     * @author Pietro Braione
     */
    private final class TestDetector implements Runnable {
        private final int testCountInitial;
        private final List<JBSEResult> items;
        private final BufferedReader evosuiteBufferedReader;
        private final Path evosuiteLogFilePath;
        private final BufferedWriter evosuiteLogFileWriter;
        private final JBSEResultInputOutputBuffer in;

        /**
         * Constructor.
         * 
         * @param testCountInitial an {@code int}, the number used to identify 
         *        the generated tests. The test generated from {@code items.get(i)}
         *        will be numbered {@code testCountInitial + i}.
         * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}, results of symbolic execution.
         * @param evosuiteInputStream the {@link InputStream} connected to the stdout of the EvoSuite process.
         * @param evosuiteLogFilePath the {@link Path} of the EvoSuite log file.
         * @param in the {@link JBSEResultInputOutputBuffer} to instruct about the
         *        path conditions EvoSuite fails to solve.
         * @throws IOException if opening a writer to the Evosuite log file fails.
         */
        public TestDetector(int testCountInitial, List<JBSEResult> items, InputStream evosuiteInputStream, Path evosuiteLogFilePath, JBSEResultInputOutputBuffer in) throws IOException {
            this.testCountInitial = testCountInitial;
            this.items = items;
            this.evosuiteBufferedReader = new BufferedReader(new InputStreamReader(evosuiteInputStream));
            this.evosuiteLogFilePath = evosuiteLogFilePath;
            this.evosuiteLogFileWriter = Files.newBufferedWriter(this.evosuiteLogFilePath);
            this.in = in;
        }

        @Override
        public void run() {
            //reads/copies the standard input and detects the generated tests
            final HashSet<Integer> generated = new HashSet<>();
            try {
                final Pattern patternEmittedTest = Pattern.compile("^.*\\* EMITTED TEST CASE: .*EvoSuiteWrapper_(\\d+), \\w+\\z");
                String line;
                while ((line = this.evosuiteBufferedReader.readLine()) != null) {
                    if (Thread.interrupted()) {
                        //the performer was shut down
                        break;
                    }
                    
                    //copies the line to the EvoSuite log file
                    this.evosuiteLogFileWriter.write(line);
                    this.evosuiteLogFileWriter.newLine();
                    
                    //check if the read line reports the emission of a test case
                    //and in the positive case schedule JBSE to analyze it
                    final Matcher matcherEmittedTest = patternEmittedTest.matcher(line);
                    if (matcherEmittedTest.matches()) {
                        final int testCount = Integer.parseInt(matcherEmittedTest.group(1));
                        generated.add(testCount);
                        final JBSEResult item = this.items.get(testCount - this.testCountInitial);
                        try {
                            checkTestCompileAndScheduleJBSE(testCount, item);
                        } catch (NoTestFileException e) {
                            LOGGER.error("Failed to generate the test case %s for path condition %s: the generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
                            //continue
                        } catch (NoTestFileScaffoldingException e) {
                            LOGGER.error("Failed to generate the test case %s for path condition %s: the generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
                            //continue
                        } catch (NoTestMethodException e) {
                            LOGGER.warn("Failed to generate the test case %s for path condition: %s: the generated files does not contain a test method (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.pathCondition);
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
            } catch (IOException e) {
                LOGGER.error("Unexpected I/O error while reading the standard output of an Evosuite process or writing on the corresponding log file %s", this.evosuiteLogFilePath.toString());
                LOGGER.error("Message: %s", e.toString());
                LOGGER.error("Stack trace:");
                for (StackTraceElement elem : e.getStackTrace()) {
                    LOGGER.error("%s", elem.toString());
                }
            } finally {
                try {
                    this.evosuiteLogFileWriter.close();
                } catch (IOException e) {
                    LOGGER.error("Unexpected I/O error while closing the Evosuite process log file %s", this.evosuiteLogFilePath.toString());
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                }
            }

            //ended reading EvoSuite log file: determines the test that
            //were not generated
            int testCount = this.testCountInitial;
            for (JBSEResult item : this.items) {
                if (!generated.contains(testCount)) {
                    //logs the items whose test cases were not generated
                    LOGGER.info("Failed to generate a test case for path condition: %s, log file: %s, wrapper: EvoSuiteWrapper_%d", stringifyPathCondition(shorten(item.getFinalState().getPathCondition())), this.evosuiteLogFilePath.toString(), testCount);
                    
                    //learns for update of indices
                    this.in.learnPathConditionForInfeasibilityIndex(item.getFinalState().getPathCondition(), false);

                    //TODO possibly lazier updates of index
                    this.in.updateIndexInfeasibilityAndReclassify();
                }
                ++testCount;
            }
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
     * @throws FrozenStateException if {@code initialState} is frozen.
     * @throws IOFileCreationException if some I/O error occurs while creating the wrapper, the directory 
     *         that must contain it, or the compilation log file.
     * @throws CompilationFailedWrapperException if the compilation of the wrapper class fails.
     */
    private void emitAndCompileEvoSuiteWrapper(int testCount, State initialState, State finalState, Map<Long, String> stringLiterals, Set<Long> stringOthers) 
    throws FrozenStateException, IOFileCreationException, CompilationFailedWrapperException {
        final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount, () -> initialState, true);
        fmt.setStringsConstant(stringLiterals);
        fmt.setStringsNonconstant(stringOthers);
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
     * tests (common to seeds and nonseeds).
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
        return retVal;
    }
    
    /**
     * Builds the command line for invoking EvoSuite for the generation of the
     * seed tests.
     * 
     * @param item a {@link JBSEResult} such that {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true && }{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == false}.
     *        All the items in {@code items} must refer to the same target method, i.e., must have same
     *        {@link JBSEResult#getTargetMethodClassName() class name}, {@link JBSEResult#getTargetMethodDescriptor() method descriptor}, and 
     *        {@link JBSEResult#getTargetMethodName() method name}.
     * @return a command line in the format of an {@link ArrayList}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     */
    private ArrayList<String> buildEvoSuiteCommandSeed(JBSEResult item) {
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
     *        All the items in {@code items} must refer to the same target method, i.e., must have same
     *        {@link JBSEResult#getTargetMethodClassName() class name}, {@link JBSEResult#getTargetMethodDescriptor() method descriptor}, and 
     *        {@link JBSEResult#getTargetMethodName() method name}.
     * @return a command line in the format of an {@link ArrayList}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     */
    private ArrayList<String> buildEvoSuiteCommand(int testCountInitial, List<JBSEResult> items) {
        final String targetClass = items.get(0).getTargetMethodClassName().replace('/', '.');
        final String targetMethodDescriptor = items.get(0).getTargetMethodDescriptor();
        final String targetMethodName = items.get(0).getTargetMethodName();
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
    
    private static final class RenamerVisitor extends ModifierVisitor<Void> {
        private final String from, to;
        
        public RenamerVisitor(String from, String to) {
            this.from = from;
            this.to = to;
        }
        
        @Override
        public Visitable visit(SimpleName n, Void arg) {
            if (n.asString().equals(this.from)) {
                n.setIdentifier(this.to);
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(Name n, Void arg) {
            if (n.asString().equals(this.from)) {
                n.setIdentifier(this.to);
            }
            return super.visit(n, arg);
        }
    }

    /**
     * Splits a seed test class generated by EvoSuite, 
     * with multiple test methods, into multiple classes
     * with a single test method.
     * 
     * @param testCountInitial an {@code int}, the number used to identify 
     *        the generated tests. The test generated will be numbered starting 
     *        from {@code testCountInitial} henceforth.
     * @param item a {@link JBSEResult}. It must be
     *        {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true && item.}{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == false}.
     * @return a {@link List}{@code <}{@link JBSEResult}{@code >}.
     * @throws IOException if some I/O error occurs during the execution of Javaparser.
     * @throws IOFileCreationException if some I/O error occurs while creating the 
     *         test class or scaffolding class, or the folder that must contain them.
     * @throws NoTestFileException if the test file does not exist.
     * @throws NoTestFileScaffoldingException if the scaffolding file does not exist. 
     */
    private List<JBSEResult> splitEvosuiteSeed(int testCountInitial, JBSEResult item) 
    throws IOException, IOFileCreationException, NoTestFileException, NoTestFileScaffoldingException {
        //parses the seed compilation unit
        final String testClassName = (item.getTargetClassName() + "_Seed_Test");
        final String scaffClassName = (this.o.getEvosuiteNoDependency() ? null : testClassName + "_scaffolding");
        final Path testFile = this.o.getTmpTestsDirectoryPath().resolve(testClassName + ".java");
        final Path scaffFile = (this.o.getEvosuiteNoDependency() ? null : this.o.getTmpTestsDirectoryPath().resolve(scaffClassName + ".java"));
        if (!testFile.toFile().exists()) {
            throw new NoTestFileException(testFile);
        }
        if (scaffFile != null && !scaffFile.toFile().exists()) {
            throw new NoTestFileScaffoldingException(scaffFile);
        }
        final CompilationUnit cuTestClass = StaticJavaParser.parse(testFile);
        
        //finds all the test method declarations 
        //in the compilation unit
        final ArrayList<MethodDeclaration> testMethodDeclarations = new ArrayList<>();
        cuTestClass.findAll(MethodDeclaration.class).forEach(md -> {
            if (md.isAnnotationPresent("Test")) {
                testMethodDeclarations.add(md);
            }
        });
        
        final ArrayList<JBSEResult> retVal = new ArrayList<>();
        int testCount = testCountInitial;
        for (MethodDeclaration testMethodDeclaration : testMethodDeclarations) {
            //builds a map of variable declarations (variable names to
            //class names)
            final HashMap<String, Class<?>> varDecls = new HashMap<>();
            testMethodDeclaration.findAll(VariableDeclarator.class).forEach(vd -> {
                varDecls.put(vd.getNameAsString(), javaTypeToClass(cuTestClass, vd.getTypeAsString()));
            });
            
            //gets all the statements in the method
            final List<ExpressionStmt> stmts = testMethodDeclaration.findAll(ExpressionStmt.class);
            Collections.reverse(stmts); //from last to first one
            
            for (ExpressionStmt stmt : stmts) {
                //finds a method call
                final Expression expr;
                if (stmt.getExpression().isMethodCallExpr()) {
                    expr = stmt.getExpression();
                } else if (stmt.getExpression().isObjectCreationExpr()) { //unlikely
                    expr = stmt.getExpression();
                } else if (stmt.getExpression().isAssignExpr() && stmt.getExpression().asAssignExpr().getValue().isMethodCallExpr()) {
                    expr = stmt.getExpression().asAssignExpr().getValue();
                } else if (stmt.getExpression().isAssignExpr() && stmt.getExpression().asAssignExpr().getValue().isObjectCreationExpr()) {
                    expr = stmt.getExpression().asAssignExpr().getValue();
                } else if (stmt.getExpression().isVariableDeclarationExpr() && stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().isPresent() &&
                           stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().get().isMethodCallExpr()) {
                    expr = stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().get();
                } else if (stmt.getExpression().isVariableDeclarationExpr() && stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().isPresent() &&
                           stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().get().isObjectCreationExpr()) {
                    expr = stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().get();
                } else {
                    continue; //gives up
                }
                
                //determines the invoked method/constructor
                final String methodName = (expr instanceof MethodCallExpr ? ((MethodCallExpr) expr).getNameAsString() : "<init>" );
                final ArrayList<Class<?>> argumentTypes = new ArrayList<>();
                for (Expression e : (expr instanceof MethodCallExpr ? ((MethodCallExpr) expr).getArguments() : ((ObjectCreationExpr) expr).getArguments())) {
                    argumentTypes.add(inferType(e, varDecls));
                }
                List<String> targetMethod = null;
                visibleTargetMethodsLoop:
                for (List<String> visibleTargetMethod : this.visibleTargetMethods) {
                    if (visibleTargetMethod.get(2).equals(methodName)) {
                        final String[] visibleTargetMethodArgumentTypes = splitParametersDescriptors(visibleTargetMethod.get(1));
                        if (visibleTargetMethodArgumentTypes.length == argumentTypes.size()) {
                            boolean allMatch = true;
                            allMatchLoop:
                            for (int i = 0; i < visibleTargetMethodArgumentTypes.length; ++i) {
                                final Class<?> argClass = argumentTypes.get(i); 
                                final Class<?> targetMethodArgClass = classFileTypeToClass(visibleTargetMethodArgumentTypes[i]);
                                if (argClass != null && targetMethodArgClass != null && !targetMethodArgClass.isAssignableFrom(argClass)) {
                                    allMatch = false;
                                    break allMatchLoop;
                                }
                            }
                            if (allMatch) {
                                targetMethod = visibleTargetMethod;
                                break visibleTargetMethodsLoop;
                            }
                        }
                    }
                }
                if (targetMethod == null) {
                    continue;
                }

                //creates a new test class declaration
                final ClassOrInterfaceDeclaration testClassDeclaration = (ClassOrInterfaceDeclaration) testMethodDeclaration.getParentNode().get();
                final ClassOrInterfaceDeclaration testClassDeclarationNew = testClassDeclaration.clone();
                final MethodDeclaration testMethodDeclarationNew = testClassDeclarationNew.getMethodsBySignature(testMethodDeclaration.getNameAsString(), testMethodDeclaration.getParameters().stream().map(Parameter::getType).map(Type::toString).toArray(String[]::new)).get(0);
                final ArrayList<MethodDeclaration> toExpunge = new ArrayList<>();
                testClassDeclarationNew.findAll(MethodDeclaration.class).forEach(md -> {
                    if (md.isAnnotationPresent("Test") && !md.equals(testMethodDeclarationNew)) {
                        toExpunge.add(md);
                    }
                });
                for (MethodDeclaration md : toExpunge) {
                    testClassDeclarationNew.remove(md);
                }
                testMethodDeclarationNew.setName("test0");
                final String testClassNameNew = (item.getTargetClassName() + "_" + testCount + "_Test");
                final String testClassNameNew_Unqualified = testClassNameNew.substring(testClassNameNew.lastIndexOf('/') + 1);
                final String scaffClassNameNew = (this.o.getEvosuiteNoDependency() ? null : testClassNameNew + "_scaffolding");
                testClassDeclarationNew.setName(testClassNameNew_Unqualified);

                //creates the compilation unit for the scaffolding
                final CompilationUnit cuTestScaffNew;
                if (this.o.getEvosuiteNoDependency()) {
                    cuTestScaffNew = null;
                } else {
                    //creates a new scaffolding class declaration
                    cuTestScaffNew = StaticJavaParser.parse(scaffFile);
                    final String scaffClassName_Unqualified = scaffClassName.substring(scaffClassName.lastIndexOf('/') + 1);
                    final String scaffClassNameNew_Unqualified = scaffClassNameNew.substring(scaffClassNameNew.lastIndexOf('/') + 1);
                    final ClassOrInterfaceDeclaration scaffClassDeclarationNew = cuTestScaffNew.findFirst(ClassOrInterfaceDeclaration.class, cid ->  cid.getName().asString().equals(scaffClassName_Unqualified)).get();
                    scaffClassDeclarationNew.setName(scaffClassNameNew_Unqualified);

                    //fixes the initializeClasses method
                    final RenamerVisitor v = new RenamerVisitor(scaffClassName_Unqualified, scaffClassNameNew_Unqualified);
                    cuTestScaffNew.accept(v, null);

                    //changes the "extends" declaration of the new test class
                    //declaration to point to the new scaffolding class declaration
                    final NodeList<ClassOrInterfaceType> testClassExtensions = testClassDeclarationNew.getExtendedTypes();
                    for (Iterator<ClassOrInterfaceType> it = testClassExtensions.iterator(); it.hasNext(); ) {
                        final ClassOrInterfaceType testClassExtension = it.next();
                        if (testClassExtension.getName().asString().equals(scaffClassName_Unqualified)) {
                            it.remove();
                            break;
                        }
                    }
                    testClassDeclarationNew.addExtendedType(scaffClassNameNew_Unqualified);
                }

                //creates the compilation unit for the test class
                final CompilationUnit cuTestClassNew = cuTestClass.clone();
                final String testClassName_Unqualified = testClassName.substring(testClassName.lastIndexOf('/') + 1);
                final ClassOrInterfaceDeclaration testClassDeclarationOld = cuTestClassNew.findFirst(ClassOrInterfaceDeclaration.class, cid -> cid.getName().asString().equals(testClassName_Unqualified)).get();
                cuTestClassNew.replace(testClassDeclarationOld, testClassDeclarationNew);

                //writes the compilation units to files
                final Path testFileNew = this.o.getTmpTestsDirectoryPath().resolve(testClassNameNew + ".java");
                try {
                    Files.createDirectories(testFileNew.getParent());
                } catch (IOException e) {
                    throw new IOFileCreationException(e, testFileNew.getParent());
                }
                try (final BufferedWriter w = Files.newBufferedWriter(testFileNew)) {
                    w.write(cuTestClassNew.toString());
                } catch (IOException e) {
                    throw new IOFileCreationException(e, testFileNew);
                }
                final Path scaffFileNew;
                if (this.o.getEvosuiteNoDependency()) {
                    scaffFileNew = null; //nothing else to write
                } else {
                    scaffFileNew = this.o.getTmpTestsDirectoryPath().resolve(scaffClassNameNew + ".java");
                    try (final BufferedWriter w = Files.newBufferedWriter(scaffFileNew)) {
                        w.write(cuTestScaffNew.toString());
                    } catch (IOException e) {
                        throw new IOFileCreationException(e, testFileNew);
                    }
                }
                
                //creates the new item
                final JBSEResult newItem = new JBSEResult(targetMethod);
                retVal.add(newItem);

                ++testCount;
            }
        }
        
        return retVal;
    }
    
    private static Class<?> javaTypeToClass(CompilationUnit cu, String type) {
        if ("boolean".equals(type)) {
            return boolean.class;
        } else if ("byte".equals(type)) {
            return byte.class;
        } else if ("char".equals(type)) {
            return char.class;
        } else if ("double".equals(type)) {
            return double.class;
        } else if ("float".equals(type)) {
            return float.class;
        } else if ("int".equals(type)) {
            return int.class;
        } else if ("long".equals(type)) {
            return long.class;
        } else if ("short".equals(type)) {
            return short.class;
        } else if (type.endsWith("[]")) {
            final Class<?> memberType = javaTypeToClass(cu, type.substring(0, type.length() - 2));
            if (memberType == null) {
                return null;
            }
            return Array.newInstance(memberType, 0).getClass();
        } else { //class name
            final ClassLoader ic = getInternalClassloader();
            final String typeNoGenerics = eraseGenericParameters(type);
            final ArrayList<String> possiblePackageQualifiers = possiblePackageQualifiers(cu, typeNoGenerics);
            for (String possiblePackageQualifier : possiblePackageQualifiers) {
                String typeNameLoop = typeNoGenerics;
                do {
                    Class<?> retVal = null;
                    try {
                        retVal = Class.forName(possiblePackageQualifier + typeNameLoop);
                    } catch (ClassNotFoundException e) {
                        try {
                            retVal = ic.loadClass(possiblePackageQualifier + typeNameLoop);
                        } catch (ClassNotFoundException e1) {
                            retVal = null;
                        }
                    }
                    if (retVal != null) {
                        return retVal;
                    }
                    //tries to replace the last dot with a dollar and reload
                    final int lastIndexOfDot = typeNameLoop.lastIndexOf('.');
                    if (lastIndexOfDot == -1) {
                        //nothing more to try with this package qualifier
                        break;
                    }
                    final StringBuilder newTypeNameLoop = new StringBuilder(typeNameLoop);
                    newTypeNameLoop.setCharAt(lastIndexOfDot, '$');
                    typeNameLoop = newTypeNameLoop.toString();
                } while (true);
            }
            return null; //nothing found
        }
    }
    
    private static String eraseGenericParameters(String type) {
        final StringBuilder retVal = new StringBuilder();
        int level = 0;
        for (int i = 0; i < type.length(); ++i) {
            final char current = type.charAt(i);
            if (current == '<') {
                ++level;
            } else if (current == '>') {
                --level;
            } else if (level == 0) {
                retVal.append(current);
            }
        }
        return retVal.toString();
    }
    
    private static ArrayList<String> possiblePackageQualifiers(CompilationUnit cu, String type) {
        final ArrayList<String> retVal = new ArrayList<>();
        retVal.add(""); //always tries with no package qualifier
        retVal.add("java.lang."); //always tries with java.lang (for standard classes that are not imported)
        cu.findAll(ImportDeclaration.class).forEach(id -> {
            final String idString = id.getNameAsString();
            if (id.isAsterisk()) {
                retVal.add(idString + ".");
            } else {
                //if type is A.B.C tries first A, then A.B, then A.B.C
                for (int i = 0; i <= type.length(); ++i) {
                    if (i == type.length() || type.charAt(i) == '.') {
                        final String typePrefix = type.substring(0, i);
                        if (idString.endsWith("." + typePrefix)) {
                            retVal.add(idString.substring(0, idString.length() - typePrefix.length()));
                        }
                    }
                }
            } //else, do not add it
        });
        return retVal;
    }
    
    private static Class<?> inferType(Expression e, HashMap<String, Class<?>> varDecls) {
        if (e.isBooleanLiteralExpr()) {
            return boolean.class;
        } else if (e.isCharLiteralExpr()) {
            return char.class;
        } else if (e.isDoubleLiteralExpr()) {
            return double.class;
        } else if (e.isIntegerLiteralExpr()) {
            return int.class;
        } else if (e.isLongLiteralExpr()) {
            return long.class;
        } else if (e.isArrayAccessExpr()) {
            final Class<?> memberType = inferType(e.asArrayAccessExpr().getName(), varDecls);
            if (memberType == null) {
                return null;
            }
            return Array.newInstance(memberType, 0).getClass();
        } else if (e.isNameExpr() && varDecls.containsKey(e.asNameExpr().getNameAsString())) {
            return varDecls.get(e.asNameExpr().getNameAsString());
        } else {
            return null; //gives up
        }
    }
    
    private static Class<?> classFileTypeToClass(String type) {
        final ClassLoader ic = getInternalClassloader();
        final String typeName = internalToBinaryTypeName(type);
        Class<?> retVal = null;
        try {
            retVal = Class.forName(typeName);
        } catch (ClassNotFoundException e) {
            try {
                retVal = ic.loadClass(typeName);
            } catch (ClassNotFoundException e1) {
                retVal = null;
            }
        }
        return retVal; 
    }
    
    private static String internalToBinaryTypeName(String type) {
        if ("B".equals(type)) {
            return "byte";
        } else if ("C".equals(type)) {
            return "char";
        } else if ("D".equals(type)) {
            return "double";
        } else if ("F".equals(type)) {
            return "float";
        } else if ("I".equals(type)) {
            return "int";
        } else if ("J".equals(type)) {
            return "long";
        } else if ("S".equals(type)) {
            return "short";
        } else if ("Z".equals(type)) {
            return "boolean";
        } else if (type.startsWith("L")){
            return type.substring(1, type.length() - 1).replace('/', '.');
        } else { //array, starts with '['
            return '[' + internalToBinaryTypeName(type.substring(1));
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
        final List<Clause> pathCondition = (item.getFinalState() == null ? null : item.getFinalState().getPathCondition());
        final String pathConditionString = (pathCondition == null ? "true" : stringifyPathCondition(shorten(pathCondition)));
        
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
            LOGGER.info("Generated test case %s, depth: %d, path condition: %s", testCaseClassName, depth, pathConditionString);
            final TestCase newTestCase = new TestCase(testCaseClassName, "()V", "test0", this.o.getTmpTestsDirectoryPath(), (testCaseScaff != null));
            this.getOutputBuffer().add(new EvosuiteResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), newTestCase, depth + 1));
        } catch (NoSuchMethodException e) { 
            throw new NoTestMethodException(testCase, pathConditionString);
        } catch (SecurityException | NoClassDefFoundError | ClassNotFoundException e) {
            throw new ClassFileAccessException(e, testCaseClassName);
        }
    }
}
