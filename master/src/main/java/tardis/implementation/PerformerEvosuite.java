package tardis.implementation;

import static jbse.bc.ClassLoaders.CLASSLOADER_APP;
import static jbse.common.Type.splitParametersDescriptors;

import static tardis.implementation.Util.getTargetMethods;
import static tardis.implementation.Util.shorten;
import static tardis.implementation.Util.stream;
import static tardis.implementation.Util.stringifyPathCondition;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
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
    private final Path jbseLibraryPath;
    private final List<Path> classesPath;
    private final String classesPathString;
    private final Path tmpPath;
    private final Path tmpBinPath;
    private final Path tmpWrapPath;
    private final Path evosuitePath;
    private final Path sushiLibPath;
    private final Path outPath;
    private final long timeBudgetSeconds;
    private final boolean evosuiteNoDependency;
    private final boolean useMOSA;
    private final String classpathEvosuite;
    private final String classpathCompilationTest;
    private final String classpathCompilationWrapper;
    private int testCount;
    private volatile boolean stopForSeeding;

    
    public PerformerEvosuite(Options o, InputBuffer<JBSEResult> in, OutputBuffer<EvosuiteResult> out) throws PerformerEvosuiteInitException {
        super(in, out, o.getNumOfThreads(), (o.getUseMOSA() ? o.getNumMOSATargets() : 1), o.getThrottleFactorEvosuite(), o.getTimeoutMOSATaskCreationDuration(), o.getTimeoutMOSATaskCreationUnit());
        try {
            this.visibleTargetMethods = getTargetMethods(o);
        } catch (ClassNotFoundException | MalformedURLException | SecurityException e) {
            throw new PerformerEvosuiteInitException(e);
        }
        this.compiler = ToolProvider.getSystemJavaCompiler();
        if (this.compiler == null) {
            throw new PerformerEvosuiteInitException("Failed to find a system Java compiler. Did you install a JDK?");
        }
        this.jbseLibraryPath = o.getJBSELibraryPath();
        this.classesPath = o.getClassesPath();
        this.classesPathString = String.join(File.pathSeparator, stream(o.getClassesPath()).map(Object::toString).toArray(String[]::new)); 
        this.tmpPath = o.getTmpDirectoryPath();
        this.tmpWrapPath = o.getTmpWrappersDirectoryPath();
        this.tmpBinPath = o.getTmpBinDirectoryPath();
        this.evosuitePath = o.getEvosuitePath();
        this.sushiLibPath = o.getSushiLibPath();
        this.outPath = o.getOutDirectory();
        this.timeBudgetSeconds = o.getEvosuiteTimeBudgetUnit().toSeconds(o.getEvosuiteTimeBudgetDuration());
        this.evosuiteNoDependency = o.getEvosuiteNoDependency();
        this.useMOSA = o.getUseMOSA();
        this.classpathEvosuite = this.classesPathString + File.pathSeparator + this.sushiLibPath.toString() + (this.useMOSA ? "" : (File.pathSeparator + this.tmpBinPath.toString()));
        this.classpathCompilationTest = this.tmpBinPath.toString() + File.pathSeparator + this.classesPathString + File.pathSeparator + this.sushiLibPath.toString() + File.pathSeparator + this.evosuitePath.toString();
        this.classpathCompilationWrapper = this.classesPathString + File.pathSeparator + this.sushiLibPath.toString();
        this.testCount = (o.getInitialTestCase() == null ? 0 : 1);
        this.stopForSeeding = false;
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
        final boolean isTargetAMethod = (item.getTargetClassName() == null);
        
        if (isTargetAMethod) {
            //builds the EvoSuite wrapper
            emitAndCompileEvoSuiteWrapperSeed(testCountInitial, item);
            
            //builds the EvoSuite command line
            final List<String> evosuiteCommand = buildEvoSuiteCommand(testCountInitial, Collections.singletonList(item));

            //launches EvoSuite
            final Path evosuiteLogFilePath = this.tmpPath.resolve("evosuite-log-" + testCountInitial + ".txt");
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
        } else {
            //builds the EvoSuite command line
            final List<String> evosuiteCommand = buildEvoSuiteCommandSeed(item); 

            //launches EvoSuite
            final Path evosuiteLogFilePath = this.tmpPath.resolve("evosuite-log-seed.txt");
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
        if (!this.useMOSA && items.size() != 1) {
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
            final Path evosuiteLogFilePath = this.tmpPath.resolve("evosuite-log-" + testCount + ".txt");
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
     * @return a {@link Path}, the file path of the generated EvoSuite wrapper.
     */
    private Path emitEvoSuiteWrapper(int testCount, State initialState, State finalState, Map<Long, String> stringLiterals) {
        final StateFormatterSushiPathCondition fmt = new StateFormatterSushiPathCondition(testCount, () -> initialState, true);
        fmt.setConstants(stringLiterals);
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
        final String initialCurrentClassPackageName = initialCurrentClassName.substring(0, initialCurrentClassName.lastIndexOf('/'));
        final Path wrapperDirectoryPath = this.tmpWrapPath.resolve(initialCurrentClassPackageName);
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
            final Classpath cp = new Classpath(this.jbseLibraryPath,
                                               Paths.get(System.getProperty("java.home", "")), 
                                               new ArrayList<>(Arrays.stream(System.getProperty("java.ext.dirs", "").split(File.pathSeparator))
                                               .map(s -> Paths.get(s)).collect(Collectors.toList())), 
                                               this.classesPath);
            final State s = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, cp, ClassFileFactoryJavassist.class, new HashMap<>(), new HashMap<>(), new SymbolFactory());
            final ClassFile cf = s.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, item.getTargetMethodClassName(), true);
            s.pushFrameSymbolic(cf, new Signature(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName()));
            final Path wrapperFilePath = emitEvoSuiteWrapper(testCount, s, s.clone(), Collections.emptyMap());
            final Path javacLogFilePath = this.tmpPath.resolve("javac-log-wrapper-" + testCount + ".txt");
            final String[] javacParameters = { "-cp", this.classpathCompilationWrapper, "-d", this.tmpBinPath.toString(), wrapperFilePath.toString() };
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
            final Path wrapperFilePath = emitEvoSuiteWrapper(i, initialState, finalState, stringLiterals);
            final Path javacLogFilePath = this.tmpPath.resolve("javac-log-wrapper-" + i + ".txt");
            final String[] javacParameters = { "-cp", this.classpathCompilationWrapper, "-d", this.tmpBinPath.toString(), wrapperFilePath.toString() };
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
        retVal.add("java");
        retVal.add("-Xmx4G");
        retVal.add("-jar");
        retVal.add(this.evosuitePath.toString());
        retVal.add("-class");
        retVal.add(targetClass);
        retVal.add("-mem");
        retVal.add("2048");
        retVal.add("-DCP=" + this.classpathEvosuite); 
        retVal.add("-Dassertions=false");
        retVal.add("-Dglobal_timeout=" + this.timeBudgetSeconds);
        retVal.add("-Dreport_dir=" + this.tmpPath.toString());
        retVal.add("-Dsearch_budget=" + this.timeBudgetSeconds);
        retVal.add("-Dtest_dir=" + this.tmpPath.toString());
        retVal.add("-Dvirtual_fs=false");
        retVal.add("-Dselection_function=ROULETTEWHEEL");
        retVal.add("-Dcriterion=BRANCH");                
        retVal.add("-Dinline=false");
        retVal.add("-Dsushi_modifiers_local_search=true");
        retVal.add("-Duse_minimizer_during_crossover=true");
        retVal.add("-Davoid_replicas_of_individuals=true"); 
        retVal.add("-Dno_change_iterations_before_reset=30");
        retVal.add("-Djunit_suffix=" + "_Seed_Test");
        if (this.evosuiteNoDependency) {
            retVal.add("-Dno_runtime_dependency");
        }
        if (this.useMOSA) {
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
        retVal.add("java");
        retVal.add("-Xmx4G");
        retVal.add("-jar");
        retVal.add(this.evosuitePath.toString());
        retVal.add("-class");
        retVal.add(targetClass);
        retVal.add("-mem");
        retVal.add("2048");
        retVal.add("-DCP=" + this.classpathEvosuite); 
        retVal.add("-Dassertions=false");
        retVal.add("-Dglobal_timeout=" + this.timeBudgetSeconds);
        retVal.add("-Dreport_dir=" + this.tmpPath.toString());
        retVal.add("-Dsearch_budget=" + this.timeBudgetSeconds);
        retVal.add("-Dtest_dir=" + this.outPath.toString());
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
        if (this.evosuiteNoDependency) {
            retVal.add("-Dno_runtime_dependency");
        }
        if (this.useMOSA) {
            retVal.add("-Dpath_condition_evaluators_dir=" + this.tmpBinPath.toString());
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
     * @return an {@code int}, the final value of test count, i.e., {@code testCountInitial}
     *         plus the total number of generated tests.
     * @throws IOException if it fails to open the test class or scaffolding class
     *         produced by the seed process
     */
    private List<JBSEResult> splitEvosuiteSeed(int testCountInitial, JBSEResult item) throws IOException {
        //parses the seed compilation unit
        final String testClassName = (item.getTargetClassName() + "_Seed_Test");
        final String scaffClassName = (this.evosuiteNoDependency ? null : testClassName + "_scaffolding");
        final Path testFile = this.tmpPath.resolve(testClassName + ".java");
        final Path scaffFile = (this.evosuiteNoDependency ? null : this.tmpPath.resolve(scaffClassName + ".java"));
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
            final HashMap<String, String> varDecls = new HashMap<>();
            testMethodDeclaration.findAll(VariableDeclarator.class).forEach(vd -> {
                varDecls.put(vd.getNameAsString(), typePattern(vd.getTypeAsString()));
            });
            
            //gets all the statements in the method
            final List<ExpressionStmt> stmts = testMethodDeclaration.findAll(ExpressionStmt.class);
            Collections.reverse(stmts); //from last to first one
            
            for (ExpressionStmt stmt : stmts) {
                //finds a method call
                final MethodCallExpr methodCallExpr;
                if (stmt.getExpression().isMethodCallExpr()) {
                    methodCallExpr = stmt.getExpression().asMethodCallExpr();
                } else if (stmt.getExpression().isAssignExpr() && stmt.getExpression().asAssignExpr().getValue().isMethodCallExpr()) {
                    methodCallExpr = stmt.getExpression().asAssignExpr().getValue().asMethodCallExpr();
                } else if (stmt.getExpression().isVariableDeclarationExpr() && stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().isPresent() &&
                           stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().get().isMethodCallExpr()) {
                    methodCallExpr = stmt.getExpression().asVariableDeclarationExpr().getVariable(0).getInitializer().get().asMethodCallExpr();
                } else {
                    continue; //gives up
                }
                
                //determines the invoked method
                final String methodName = methodCallExpr.getNameAsString();
                final ArrayList<String> argumentTypes = new ArrayList<>();
                for (Expression e : methodCallExpr.getArguments()) {
                    argumentTypes.add(inferType(e, varDecls));
                }
                List<String> targetMethod = null;
                visibleTargetMethodsLoop:
                for (List<String> visibleTargetMethod : this.visibleTargetMethods) {
                    if (visibleTargetMethod.get(2).equals(methodName)) {
                        String[] visibleTargetMethodArgumentTypes = splitParametersDescriptors(visibleTargetMethod.get(1));
                        if (visibleTargetMethodArgumentTypes.length == argumentTypes.size()) {
                            boolean allMatch = true;
                            allMatchLoop:
                            for (int i = 0; i < visibleTargetMethodArgumentTypes.length; ++i) {
                                final Pattern p = Pattern.compile(argumentTypes.get(i));
                                final Matcher m = p.matcher(visibleTargetMethodArgumentTypes[i]);
                                if (!m.matches()) {
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
                final String scaffClassNameNew = (this.evosuiteNoDependency ? null : testClassNameNew + "_scaffolding");
                testClassDeclarationNew.setName(testClassNameNew_Unqualified);

                //creates the compilation unit for the scaffolding
                final CompilationUnit cuTestScaffNew;
                if (this.evosuiteNoDependency) {
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
                final Path testFileNew = this.outPath.resolve(testClassNameNew + ".java");
                Files.createDirectories(testFileNew.getParent());
                try (final BufferedWriter w = Files.newBufferedWriter(testFileNew)) {
                    w.write(cuTestClassNew.toString());
                }
                if (this.evosuiteNoDependency) {
                    //nothing else to write
                } else {
                    final Path scaffFileNew = this.outPath.resolve(scaffClassNameNew + ".java");
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
    
    private static String typePattern(String type) {
        if ("boolean".equals(type)) {
            return "Z";
        } else if ("byte".equals(type)) {
            return "B";
        } else if ("char".equals(type)) {
            return "C";
        } else if ("double".equals(type)) {
            return "D";
        } else if ("float".equals(type)) {
            return "F";
        } else if ("int".equals(type)) {
            return "I";
        } else if ("long".equals(type)) {
            return "J";
        } else if ("short".equals(type)) {
            return "S";
        } else if (type.endsWith("[]")) {
            return "\\[" + typePattern(type.substring(0, type.length() - 2));
        } else {
            return "L.*" + type.replace(".", "(\\$|/)") + ";";
        }
    }
    
    private static String inferType(Expression e, HashMap<String, String> varDecls) {
        if (e.isBooleanLiteralExpr()) {
            return "Z";
        } else if (e.isCharLiteralExpr()) {
            return "C";
        } else if (e.isDoubleLiteralExpr()) {
            return "D";
        } else if (e.isIntegerLiteralExpr()) {
            return "I";
        } else if (e.isLongLiteralExpr()) {
            return "J";
        } else if (e.isArrayAccessExpr()) {
            return "[" + inferType(e.asArrayAccessExpr().getName(), varDecls);
        } else if (e.isNameExpr() && varDecls.containsKey(e.asNameExpr().getNameAsString())) {
            return varDecls.get(e.asNameExpr().getNameAsString());
        } else {
            return ".*"; //gives up
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
            final URLClassLoader cloader = URLClassLoader.newInstance(new URL[]{ this.tmpBinPath.toUri().toURL(), this.evosuitePath.toUri().toURL() }); 
            cloader.loadClass(className.replace('/',  '.')).getDeclaredMethod("test0");
        } catch (SecurityException | NoClassDefFoundError | ClassNotFoundException | MalformedURLException e) {
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
        final Path testCaseScaff = (this.evosuiteNoDependency ? null : this.outPath.resolve(testCaseClassName + "_scaffolding.java"));
        final Path testCase = this.outPath.resolve(testCaseClassName + ".java");
        if (!testCase.toFile().exists() || (testCaseScaff != null && !testCaseScaff.toFile().exists())) {
            System.out.println("[EVOSUITE] Failed to generate the test case " + testCaseClassName + " for path condition: " + pathCondition + ": the generated files do not seem to exist");
            return;
        }

        //compiles the generated test
        final Path javacLogFilePath = this.tmpPath.resolve("javac-log-test-" +  testCount + ".txt");
        final String[] javacParametersTestCase = { "-cp", this.classpathCompilationTest, "-d", this.tmpBinPath.toString(), testCase.toString() };
        try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
            if (testCaseScaff != null) {
                final String[] javacParametersTestScaff = { "-cp", this.classpathCompilationTest, "-d", this.tmpBinPath.toString(), testCaseScaff.toString() };
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
            final TestCase newTC = new TestCase(testCaseClassName, "()V", "test0", this.outPath);
            this.getOutputBuffer().add(new EvosuiteResult(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName(), newTC, depth + 1)); //TODO what if the item has not a target method, as it happens with seeds???
        } catch (NoSuchMethodException e) { 
            //EvoSuite failed to generate the test case, thus we just ignore it 
            System.out.println("[EVOSUITE] Failed to generate the test case " + testCaseClassName + " for path condition: " + pathCondition + ": the generated file does not contain a test method");
        }
    }
}
