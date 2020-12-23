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
import tardis.framework.InputBuffer;
import tardis.framework.OutputBuffer;
import tardis.framework.Performer;

/**
 * A {@link Performer} that consumes {@link JBSEResult}s by invoking Evosuite
 * to build tests from path conditions. Upon success the produced tests are 
 * emitted as {@link EvosuiteResult}s.
 * 
 * @author Pietro Braione
 *
 */
public final class PerformerEvosuite extends Performer<JBSEResult, EvosuiteResult> {
    private final List<List<String>> visibleTargetMethods;
    private final JavaCompiler compiler;
    private final Options o;
    private final long timeBudgetSeconds;
    private final String classpathEvosuite;
    private final URL[] classpathTestURLClassLoader;
    private final String classpathCompilationTest;
    private final String classpathCompilationWrapper;
    private int testCount;
    private volatile boolean stopForSeeding;

    
    public PerformerEvosuite(Options o, InputBuffer<JBSEResult> in, OutputBuffer<EvosuiteResult> out) throws PerformerEvosuiteInitException {
        super(in, out, o.getNumOfThreadsEvosuite(), (o.getUseMOSA() ? o.getNumMOSATargets() : 1), o.getThrottleFactorEvosuite(), o.getTimeoutMOSATaskCreationDuration(), o.getTimeoutMOSATaskCreationUnit());
        try {
            this.visibleTargetMethods = getTargets(o);
        } catch (ClassNotFoundException | MalformedURLException | SecurityException e) {
            throw new PerformerEvosuiteInitException(e);
        }
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new PerformerEvosuiteInitException("Failed to find a system Java compiler. Did you install a JDK?");
        }
        this.o = o;
        this.timeBudgetSeconds = o.getEvosuiteTimeBudgetUnit().toSeconds(o.getEvosuiteTimeBudgetDuration());
        final String classesPathString = String.join(File.pathSeparator, stream(o.getClassesPath()).map(Object::toString).toArray(String[]::new)); 
        this.classpathEvosuite = classesPathString + File.pathSeparator + this.o.getJBSELibraryPath().toString() + File.pathSeparator + this.o.getSushiLibPath().toString() + (this.o.getUseMOSA() ? "" : (File.pathSeparator + this.o.getTmpBinDirectoryPath().toString()));
        final ArrayList<Path> classpathTestPath = new ArrayList<>(o.getClassesPath());
        classpathTestPath.add(this.o.getSushiLibPath());
        classpathTestPath.add(this.o.getTmpBinDirectoryPath());
        classpathTestPath.add(this.o.getEvosuitePath());
        this.classpathTestURLClassLoader = stream(classpathTestPath).map(PerformerEvosuite::toURL).toArray(URL[]::new);
        this.classpathCompilationTest = this.o.getTmpBinDirectoryPath().toString() + File.pathSeparator + classesPathString + File.pathSeparator + this.o.getJBSELibraryPath().toString() + File.pathSeparator + this.o.getSushiLibPath().toString() + File.pathSeparator + this.o.getEvosuitePath().toString();
        this.classpathCompilationWrapper = classesPathString + File.pathSeparator + this.o.getSushiLibPath().toString();
        this.testCount = (o.getInitialTestCase() == null ? 0 : 1);
        this.stopForSeeding = false;
    }
    
    private static URL toURL(Path path) {
        try {
            return path.toUri().toURL();
        } catch (MalformedURLException e) {
            System.out.println("[EVOSUITE] Unexpected error while converting " + path.toString() + " to URL: " + e);
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
                emitAndCompileEvoSuiteWrapperSeed(testCountInitial, item);

                //builds the EvoSuite command line
                final List<String> evosuiteCommand = buildEvoSuiteCommand(testCountInitial, Collections.singletonList(item));

                //launches EvoSuite
                final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-" + testCountInitial + ".txt");
                final Process processEvosuite;
                try {
                    processEvosuite = launchProcess(evosuiteCommand, evosuiteLogFilePath);
                    System.out.println("[EVOSUITE] Launched EvoSuite seed process, command line: " + evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
                } catch (IOException e) {
                    System.out.println("[EVOSUITE] Unexpected I/O error while running EvoSuite: " + e);
                    return; //TODO throw an exception?
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
                checkTestCompileAndScheduleJBSE(testCountInitial, item);

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
                    System.out.println("[EVOSUITE] Launched EvoSuite seed process, command line: " + evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
                } catch (IOException e) {
                    System.out.println("[EVOSUITE] Unexpected I/O error while running EvoSuite seed: " + e);
                    return; //TODO throw an exception?
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
                } catch (IOException e) {
                    System.out.println("[EVOSUITE] Unexpected I/O error while splitting EvoSuite seed: " + e);
                    return; //TODO throw an exception?
                }

                //schedules JBSE
                int testCount = testCountInitial;
                for (JBSEResult splitItem : splitItems) {
                    checkTestCompileAndScheduleJBSE(testCount, splitItem);
                    ++testCount;
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
        if (!this.o.getUseMOSA() && items.size() != 1) {
            System.out.println("[EVOSUITE] Unexpected internal error: MOSA is not used but the number of targets passed to EvoSuite is different from 1");
            return; //TODO throw an exception?
        }

        //splits items in sublists having same target method
        final Map<String, List<JBSEResult>> splitItems = 
        items.stream().collect(Collectors.groupingBy(r -> r.getTargetMethodClassName() + ":" + r.getTargetMethodDescriptor() + ":" + r.getTargetMethodName()));

        //launches an EvoSuite process for each sublist
        final ArrayList<Thread> threads = new ArrayList<>();
        int testCountStart = testCountInitial;
        for (List<JBSEResult> subItems : splitItems.values()) {
            final int testCount = testCountStart; //copy into final variable to keep compiler happy
            testCountStart += subItems.size(); //for the next iteration

            //generates and compiles the wrappers
            emitAndCompileEvoSuiteWrappers(testCount, subItems);

            //builds the EvoSuite command line
            final List<String> evosuiteCommand = buildEvoSuiteCommand(testCount, subItems); 

            //launches EvoSuite
            final Path evosuiteLogFilePath = this.o.getTmpDirectoryPath().resolve("evosuite-log-" + testCount + ".txt");
            final Process processEvosuite;
            try {
                processEvosuite = launchProcess(evosuiteCommand, evosuiteLogFilePath);
                System.out.println("[EVOSUITE] Launched EvoSuite process, command line: " + evosuiteCommand.stream().reduce("", (s1, s2) -> { return s1 + " " + s2; }));
            } catch (IOException e) {
                System.out.println("[EVOSUITE] Unexpected I/O error while running EvoSuite: " + e);
                return; //TODO throw an exception?
            }

            //launches a thread that waits for tests and schedules 
            //JBSE for exploring them
            final TestDetector tdJBSE = new TestDetector(testCount, subItems, evosuiteLogFilePath);
            final Thread tJBSE = new Thread(tdJBSE);
            tJBSE.start();
            threads.add(tJBSE);

            //launches another thread that waits for EvoSuite to end
            //and then alerts the previous thread
            final Thread tEvosuiteEnd = new Thread(() -> {
                try {
                    processEvosuite.waitFor();
                } catch (InterruptedException e) {
                    //the performer was shut down: kill the EvoSuite job
                    processEvosuite.destroy();
                }
                tdJBSE.ended = true;
            });
            tEvosuiteEnd.start();
            threads.add(tEvosuiteEnd);
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
     * Emits the EvoSuite wrapper (file .java) for the path condition of some state.
     * 
     * @param testCount an {@code int}, the number used to identify the test.
     * @param initialState a {@link State}; must be the initial state in the execution 
     *        for which we want to generate the wrapper.
     * @param finalState a {@link State}; must be the final state in the execution 
     *        for which we want to generate the wrapper.
     * @param stringLiterals a {@link Map}{@code <}{@link Long}{@code , }{@link String}{@code >}, 
     *         mapping a heap position of a {@link String} literal to the
     *         corresponding value of the literal.
     * @param stringOthers a {@link List}{@code <}{@link Long}{@code >}, 
     *         listing the heap positions of the nonconstant {@link String}s.
     * @return a {@link Path}, the file path of the generated EvoSuite wrapper.
     */
    private Path emitEvoSuiteWrapper(int testCount, State initialState, State finalState, Map<Long, String> stringLiterals, Set<Long> stringOthers) {
        final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount, () -> initialState, true);
        fmt.setStringsConstant(stringLiterals);
        fmt.setStringsNonconstant(stringOthers);
        fmt.formatPrologue();
        fmt.formatState(finalState);
        fmt.formatEpilogue();

        final String initialCurrentClassName;
        try {
            initialCurrentClassName = initialState.getStack().get(0).getMethodClass().getClassName();
        } catch (FrozenStateException e) {
            System.out.println("[EVOSUITE] Unexpected internal error while creating EvoSuite wrapper directory: The initial state is frozen.");
            fmt.cleanup();
            return null; //TODO throw an exception
        }
        
        final int lastSlash = initialCurrentClassName.lastIndexOf('/');
        final String initialCurrentClassPackageName = (lastSlash == -1 ? "" : initialCurrentClassName.substring(0, lastSlash));
        final Path wrapperDirectoryPath = this.o.getTmpWrappersDirectoryPath().resolve(initialCurrentClassPackageName);
        try {
            Files.createDirectories(wrapperDirectoryPath);
        } catch (IOException e) {
            System.out.println("[EVOSUITE] Unexpected I/O error while creating EvoSuite wrapper directory " + wrapperDirectoryPath.toString() + ": " + e);
            fmt.cleanup();
            return null; //TODO throw an exception
        }
        final Path wrapperFilePath = wrapperDirectoryPath.resolve("EvoSuiteWrapper_" + testCount + ".java");
        try (final BufferedWriter w = Files.newBufferedWriter(wrapperFilePath)) {
            w.write(fmt.emit());
        } catch (IOException e) {
            System.out.println("[EVOSUITE] Unexpected I/O error while creating EvoSuite wrapper " + wrapperFilePath.toString() + ": " + e);
            fmt.cleanup();
            return null; //TODO throw an exception
        }
        fmt.cleanup();

        return wrapperFilePath;
    }
    
    /**
     * Emits and compiles a dummy EvoSuite wrapper that causes the generation
     * of a single test case for exactly one method.
     * 
     * @param testCount an {@code int}, the number used to identify the generated 
     *        test.
     * @param items the seed {@link JBSEResult}.
     */
    private void emitAndCompileEvoSuiteWrapperSeed(int testCount, JBSEResult item) {
        try {
            final Classpath cp = new Classpath(this.o.getJBSELibraryPath(),
                                               Paths.get(System.getProperty("java.home", "")), 
                                               new ArrayList<>(Arrays.stream(System.getProperty("java.ext.dirs", "").split(File.pathSeparator))
                                               .map(s -> Paths.get(s)).collect(Collectors.toList())), 
                                               this.o.getClassesPath());
            final State s = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, cp, ClassFileFactoryJavassist.class, new HashMap<>(), new HashMap<>(), new SymbolFactory());
            final ClassFile cf = s.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, item.getTargetMethodClassName(), true);
            s.pushFrameSymbolic(cf, new Signature(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName()));
            final Path wrapperFilePath = emitEvoSuiteWrapper(testCount, s, s.clone(), Collections.emptyMap(), Collections.emptySet());
            final Path javacLogFilePath = this.o.getTmpDirectoryPath().resolve("javac-log-wrapper-" + testCount + ".txt");
            final String[] javacParameters = { "-cp", this.classpathCompilationWrapper, "-d", this.o.getTmpBinDirectoryPath().toString(), wrapperFilePath.toString() };
            try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
                this.compiler.run(null, w, w, javacParameters);
            } catch (IOException e) {
                System.out.println("[EVOSUITE] Unexpected I/O error while creating seed EvoSuite wrapper compilation log file " + javacLogFilePath.toString() + ": " + e);
                //TODO throw an exception
            }
        } catch (IOException | InvalidClassFileFactoryClassException | InvalidInputException | ClassFileNotFoundException | ClassFileIllFormedException | 
                 ClassFileNotAccessibleException | IncompatibleClassFileException | PleaseLoadClassException | BadClassFileVersionException | 
                 WrongClassNameException | CannotAssumeSymbolicObjectException | MethodNotFoundException | MethodCodeNotFoundException | 
                 HeapMemoryExhaustedException | RenameUnsupportedException e) {
            System.out.println("[EVOSUITE] Unexpected error while creating seed EvoSuite wrapper: " + e);
            return; //TODO throw an exception
        }

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
        int i = testCountInitial;
        for (JBSEResult item : items) {
            final State initialState = item.getInitialState();
            final State finalState = item.getFinalState();
            final Map<Long, String> stringLiterals = item.getStringLiterals();
            final Set<Long> stringOthers = item.getStringOthers();
            final Path wrapperFilePath = emitEvoSuiteWrapper(i, initialState, finalState, stringLiterals, stringOthers);
            final Path javacLogFilePath = this.o.getTmpDirectoryPath().resolve("javac-log-wrapper-" + i + ".txt");
            final String[] javacParameters = { "-cp", this.classpathCompilationWrapper, "-d", this.o.getTmpBinDirectoryPath().toString(), wrapperFilePath.toString() };
            try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
                this.compiler.run(null, w, w, javacParameters);
            } catch (IOException e) {
                System.out.println("[EVOSUITE] Unexpected I/O error while creating EvoSuite wrapper compilation log file " + javacLogFilePath.toString() + ": " + e);
                //TODO throw an exception
            }
            ++i;
        }
    }

    /**
     * Builds the command line for invoking EvoSuite for the generation of the
     * seed tests.
     * 
     * @param item a {@link JBSEResult} such that {@code item.}{@link JBSEResult#isSeed() isSeed}{@code () == true && }{@link JBSEResult#hasTargetMethod() hasTargetMethod}{@code () == false}.
     *        All the items in {@code items} must refer to the same target method, i.e., must have same
     *        {@link JBSEResult#getTargetMethodClassName() class name}, {@link JBSEResult#getTargetMethodDescriptor() method descriptor}, and 
     *        {@link JBSEResult#getTargetMethodName() method name}.
     * @return a command line in the format of a {@link List}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     */
    private List<String> buildEvoSuiteCommandSeed(JBSEResult item) {
        final boolean isTargetAMethod = (item.getTargetClassName() == null);
        final String targetClass = (isTargetAMethod ? item.getTargetMethodClassName() : item.getTargetClassName()).replace('/', '.');
        final List<String> retVal = new ArrayList<String>();
        if (this.o.getJava8Home() == null) {
            retVal.add("java");
        } else {
            retVal.add(this.o.getJava8Home().resolve("bin/java").toAbsolutePath().toString());
        }
        retVal.add("-Xmx4G");
        retVal.add("-jar");
        retVal.add(this.o.getEvosuitePath().toString());
        retVal.add("-class");
        retVal.add(targetClass);
        retVal.add("-mem");
        retVal.add("2048");
        retVal.add("-DCP=" + this.classpathEvosuite); 
        retVal.add("-Dassertions=false");
        retVal.add("-Dreport_dir=" + this.o.getTmpDirectoryPath().toString());
        retVal.add("-Dsearch_budget=" + this.timeBudgetSeconds);
        retVal.add("-Dtest_dir=" + this.o.getTmpTestsDirectoryPath().toString());
        retVal.add("-Dvirtual_fs=false");
        retVal.add("-Dselection_function=ROULETTEWHEEL");
        retVal.add("-Dcriterion=BRANCH");                
        retVal.add("-Dinline=false");
        retVal.add("-Dsushi_modifiers_local_search=true");
        retVal.add("-Duse_minimizer_during_crossover=true");
        retVal.add("-Davoid_replicas_of_individuals=true"); 
        retVal.add("-Dno_change_iterations_before_reset=30");
        retVal.add("-Djunit_suffix=" + "_Seed_Test");
        if (this.o.getEvosuiteNoDependency()) {
            retVal.add("-Dno_runtime_dependency");
        }
        if (this.o.getUseMOSA()) {
            retVal.add("-Dcrossover_function=SUSHI_HYBRID");
            retVal.add("-Dalgorithm=DYNAMOSA");
            retVal.add("-generateMOSuite");
        } else {
            retVal.add("-Dhtml=false");
            retVal.add("-Dcrossover_function=SINGLEPOINT");
            retVal.add("-Dcrossover_implementation=SUSHI_HYBRID");
            retVal.add("-Dmax_size=1");
            retVal.add("-Dmax_initial_tests=1");
        }

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
     * @return a command line in the format of a {@link List}{@code <}{@link String}{@code >},
     *         suitable to be passed to a {@link ProcessBuilder}.
     */
    private List<String> buildEvoSuiteCommand(int testCountInitial, List<JBSEResult> items) {
        final String targetClass = items.get(0).getTargetMethodClassName().replace('/', '.');
        final String targetMethodDescriptor = items.get(0).getTargetMethodDescriptor();
        final String targetMethodName = items.get(0).getTargetMethodName();
        final List<String> retVal = new ArrayList<String>();
        if (this.o.getJava8Home() == null) {
            retVal.add("java");
        } else {
            retVal.add(this.o.getJava8Home().resolve("bin/java").toAbsolutePath().toString());
        }
        retVal.add("-Xmx4G");
        retVal.add("-jar");
        retVal.add(this.o.getEvosuitePath().toString());
        retVal.add("-class");
        retVal.add(targetClass);
        retVal.add("-mem");
        retVal.add("2048");
        retVal.add("-DCP=" + this.classpathEvosuite); 
        retVal.add("-Dassertions=false");
        retVal.add("-Dreport_dir=" + this.o.getTmpDirectoryPath().toString());
        retVal.add("-Dsearch_budget=" + this.timeBudgetSeconds);
        retVal.add("-Dtest_dir=" + this.o.getTmpTestsDirectoryPath().toString());
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
        if (this.o.getEvosuiteNoDependency()) {
            retVal.add("-Dno_runtime_dependency");
        }
        if (this.o.getUseMOSA()) {
            retVal.add("-Dpath_condition_evaluators_dir=" + this.o.getTmpBinDirectoryPath().toString());
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
            final String targetPackage = targetClass.substring(0, targetClass.lastIndexOf('.'));
            optionPC.append(targetClass + "," + targetMethodName + targetMethodDescriptor + "," + targetPackage + ".EvoSuiteWrapper_" + i);
        }
        retVal.add(optionPC.toString());

        return retVal;
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
     * @throws IOException if it fails to open the test class or scaffolding class
     *         produced by the seed process
     */
    private List<JBSEResult> splitEvosuiteSeed(int testCountInitial, JBSEResult item) throws IOException {
        //parses the seed compilation unit
        final String testClassName = (item.getTargetClassName() + "_Seed_Test");
        final String scaffClassName = (this.o.getEvosuiteNoDependency() ? null : testClassName + "_scaffolding");
        final Path testFile = this.o.getTmpTestsDirectoryPath().resolve(testClassName + ".java");
        final Path scaffFile = (this.o.getEvosuiteNoDependency() ? null : this.o.getTmpTestsDirectoryPath().resolve(scaffClassName + ".java"));
        if (!testFile.toFile().exists() || (scaffFile != null && !scaffFile.toFile().exists())) {
            System.out.println("[EVOSUITE] Failed to split the seed test class " + testFile + ": the test class does not seem to exist");
            return null; //TODO throw some exception?
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
                Files.createDirectories(testFileNew.getParent());
                try (final BufferedWriter w = Files.newBufferedWriter(testFileNew)) {
                    w.write(cuTestClassNew.toString());
                }
                final Path scaffFileNew;
                if (this.o.getEvosuiteNoDependency()) {
                    scaffFileNew = null; //nothing else to write
                } else {
                    scaffFileNew = this.o.getTmpTestsDirectoryPath().resolve(scaffClassNameNew + ".java");
                    try (final BufferedWriter w = Files.newBufferedWriter(scaffFileNew)) {
                        w.write(cuTestScaffNew.toString());
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
     * Class for a {@link Runnable} that listens for the output produced by 
     * an instance of EvoSuite, and when this produces a test
     * schedules JBSE for its analysis.
     * 
     * @author Pietro Braione
     */
    private final class TestDetector implements Runnable {
        private final int testCountInitial;
        private final List<JBSEResult> items;
        private final Path evosuiteLogFilePath;
        public volatile boolean ended;

        /**
         * Constructor.
         * 
         * @param testCountInitial an {@code int}, the number used to identify 
         *        the generated tests. The test generated from {@code items.get(i)}
         *        will be numbered {@code testCountInitial + i}.
         * @param items a {@link List}{@code <}{@link JBSEResult}{@code >}, results of symbolic execution.
         * @param evosuiteLogFilePath the {@link Path} of the EvoSuite log file.
         */
        public TestDetector(int testCountInitial, List<JBSEResult> items, Path evosuiteLogFilePath) {
            this.testCountInitial = testCountInitial;
            this.items = items;
            this.evosuiteLogFilePath = evosuiteLogFilePath;
            this.ended = false;
        }

        @Override
        public void run() {
            detectTestsAndScheduleJBSE();
        }

        /**
         * Waits for EvoSuite to emit test classes and schedules JBSE
         * for their further analysis.
         */
        private void detectTestsAndScheduleJBSE() {
            final Pattern patternEmittedTest = Pattern.compile("^.*\\* EMITTED TEST CASE: .*EvoSuiteWrapper_(\\d+), \\w+\\z");
            final HashSet<Integer> generated = new HashSet<>();
            try (final BufferedReader r = Files.newBufferedReader(this.evosuiteLogFilePath)) {
                //modified from https://stackoverflow.com/a/154588/450589
                while (true) {
                    final String line = r.readLine();
                    if (line == null) { 
                        //no lines in the file
                        if (this.ended) {
                            break;
                        } else {
                            //possibly more lines in the future: wait a little bit
                            //and retry
                            Thread.sleep(2000);
                        }
                    } else {
                        //check if the read line reports the emission of a test case
                        //and in the positive case schedule JBSE to analyze it
                        final Matcher matcherEmittedTest = patternEmittedTest.matcher(line);
                        if (matcherEmittedTest.matches()) {
                            final int testCount = Integer.parseInt(matcherEmittedTest.group(1));
                            generated.add(testCount);
                            final JBSEResult item = this.items.get(testCount - this.testCountInitial);
                            checkTestCompileAndScheduleJBSE(testCount, item);
                        }
                    }
                }
            } catch (InterruptedException e) {
                //the performer was shut down:
                //just fall through
            } catch (IOException e) {
                System.out.println("[EVOSUITE] Unexpected I/O error while reading EvoSuite log file " + this.evosuiteLogFilePath.toString() + ": " + e);
                //TODO throw an exception?
            }

            //ended reading EvoSuite log file: warns about tests that 
            //have not been generated and exits
            int testCount = this.testCountInitial;
            for (JBSEResult item : this.items) {
                if (!generated.contains(testCount)) {
                    System.out.println("[EVOSUITE] Failed to generate a test case for path condition: " + stringifyPathCondition(shorten(item.getFinalState().getPathCondition())) + ", log file: " + this.evosuiteLogFilePath.toString() + ", wrapper: EvoSuiteWrapper_" + testCount);
                }
                ++testCount;
            }
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
            final URLClassLoader cloader = URLClassLoader.newInstance(this.classpathTestURLClassLoader); 
            cloader.loadClass(className.replace('/',  '.')).getDeclaredMethod("test0");
        } catch (SecurityException | NoClassDefFoundError | ClassNotFoundException e) {
            System.out.println("[EVOSUITE] Unexpected error while verifying that class " + className + " exists and has a test method: " + e);
            //TODO throw an exception
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
     */
    private void checkTestCompileAndScheduleJBSE(int testCount, JBSEResult item) {
        final List<Clause> pathConditionClauses = (item.getFinalState() == null ? null : item.getFinalState().getPathCondition());
        final String pathCondition = (pathConditionClauses == null ? "true" : stringifyPathCondition(shorten(pathConditionClauses)));
        
        //checks if EvoSuite generated the files
        final String testCaseClassName = (item.hasTargetMethod() ? item.getTargetMethodClassName() : item.getTargetClassName()) + "_" + testCount + "_Test";
        final Path testCaseScaff = (this.o.getEvosuiteNoDependency() ? null : this.o.getTmpTestsDirectoryPath().resolve(testCaseClassName + "_scaffolding.java"));
        final Path testCase = this.o.getTmpTestsDirectoryPath().resolve(testCaseClassName + ".java");
        if (!testCase.toFile().exists() || (testCaseScaff != null && !testCaseScaff.toFile().exists())) {
            System.out.println("[EVOSUITE] Failed to generate the test case " + testCaseClassName + " for path condition: " + pathCondition + ": the generated files do not seem to exist");
            return;
        }

        //compiles the generated test
        final Path javacLogFilePath = this.o.getTmpTestsDirectoryPath().resolve("javac-log-test-" +  testCount + ".txt");
        final String[] javacParametersTestCase = { "-cp", this.classpathCompilationTest, "-d", this.o.getTmpBinDirectoryPath().toString(), testCase.toString() };
        try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
            if (testCaseScaff != null) {
                final String[] javacParametersTestScaff = { "-cp", this.classpathCompilationTest, "-d", this.o.getTmpBinDirectoryPath().toString(), testCaseScaff.toString() };
                this.compiler.run(null, w, w, javacParametersTestScaff);
            }
            this.compiler.run(null, w, w, javacParametersTestCase);
        } catch (IOException e) {
            System.out.println("[EVOSUITE] Unexpected I/O error while creating test case compilation log file " + javacLogFilePath.toString() + ": " + e);
            //TODO throw an exception
        }

        //creates the TestCase and schedules it for further exploration
        try {
            checkTestExists(testCaseClassName);
            final int depth = item.getDepth();
            System.out.println("[EVOSUITE] Generated test case " + testCaseClassName + ", depth: " + depth + ", path condition: " + pathCondition);
            final TestCase newTestCase = new TestCase(testCaseClassName, "()V", "test0", this.o.getTmpTestsDirectoryPath(), (testCaseScaff != null));
            this.getOutputBuffer().add(new EvosuiteResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), newTestCase, depth + 1));
        } catch (NoSuchMethodException e) { 
            //EvoSuite failed to generate the test case, thus we just ignore it 
            System.out.println("[EVOSUITE] Failed to generate the test case " + testCaseClassName + " for path condition: " + pathCondition + ": the generated file does not contain a test method");
        }
    }
}
