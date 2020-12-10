package tardis;

import static java.nio.file.Files.createDirectory;
import static java.nio.file.Files.exists;

import static tardis.implementation.Util.stream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

import tardis.framework.TerminationManager;
import tardis.implementation.CoverageSet;
import tardis.implementation.EvosuiteResult;
import tardis.implementation.JBSEResult;
import tardis.implementation.NoJavaCompilerException;
import tardis.implementation.PerformerEvosuite;
import tardis.implementation.PerformerEvosuiteInitException;
import tardis.implementation.PerformerJBSE;
import tardis.implementation.QueueInputOutputBuffer;
import tardis.implementation.TestCase;

/**
 * TARDIS main class.
 * 
 * @author Pietro Braione
 */
public final class Main {
    /**
     * The configuration {@link Options}.
     */
    private final Options o;

    /**
     * Constructor.
     * 
     * @param o The configuration {@link Options}.
     */
    public Main(Options o) {
        this.o = o;
    }

    /**
     * Runs TARDIS.
     * 
     * @return An {@code int} exit code, {@code 0} meaning successful exit, {@code 1} meaning 
     *         exit due to an error, {@code 2} meaning exit due to an internal error.
     */
    public int start() {
        try {
            //creates the temporary directories if they do not exist
            if (!exists(o.getTmpDirectoryPath())) {
                createDirectory(o.getTmpDirectoryPath());
            }
            if (!exists(o.getTmpBinDirectoryPath())) {
                createDirectory(o.getTmpBinDirectoryPath());
            }

            //creates the coverage set data structure
            final CoverageSet coverageSet = new CoverageSet();

            //creates the communication buffers between the performers
            final QueueInputOutputBuffer<JBSEResult> pathConditionBuffer = new QueueInputOutputBuffer<>();
            final QueueInputOutputBuffer<EvosuiteResult> testCaseBuffer = new QueueInputOutputBuffer<>();

            //creates and wires together the components of the architecture
            final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
            final PerformerJBSE performerJBSE = new PerformerJBSE(this.o, testCaseBuffer, pathConditionBuffer, coverageSet);
            final TerminationManager terminationManager = new TerminationManager(this.o.getGlobalTimeBudgetDuration(), this.o.getGlobalTimeBudgetUnit(), performerJBSE, performerEvosuite);

            //seeds the initial test cases
            if (this.o.getTargetMethod() == null || this.o.getInitialTestCase() == null) {
                //the target is a class, or is a method but
                //there is no initial test case: EvoSuite should start
                final ArrayList<JBSEResult> seed = generateSeedForPerformerEvosuite();
                performerEvosuite.seed(seed);
            } else {
                //the target is a method and there is one
                //initial test case: JBSE should start
                final ArrayList<EvosuiteResult> seed = generateSeedForPerformerJBSE();
                performerJBSE.seed(seed);
            }

            //starts everything
            System.out.println("[MAIN    ] This is " + getName() + ", version " + getVersion() + ", " + '\u00a9' + " 2017-2020 " + getVendor());
            final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
            System.out.println("[MAIN    ] Starting at " + dtf.format(LocalDateTime.now()) + ", target is " + (this.o.getTargetMethod() == null ? ("class " + this.o.getTargetClass()) : ("method " + this.o.getTargetMethod().get(0) + ":" + this.o.getTargetMethod().get(1) + ":" + this.o.getTargetMethod().get(2))));
            performerJBSE.start();
            performerEvosuite.start();
            terminationManager.start();

            //waits end and prints a final message
            terminationManager.waitTermination();
            System.out.println("[MAIN    ] Ending at " + dtf.format(LocalDateTime.now()));
            return 0;
        } catch (IOException e) {
            System.err.println("[MAIN    ] Error: Unexpected I/O error");
            System.err.println("[MAIN    ] Message: " + e);
            return 1;
        } catch (PerformerEvosuiteInitException e) {
            System.err.println("[MAIN    ] Error: Unable to create the Evosuite performer");
            System.err.println("[MAIN    ] Message: " + e);
            return 1;
        } catch (SecurityException e) {
            System.err.println("[MAIN    ] Error: The security manager did not allow to get the system class loader");
            System.err.println("[MAIN    ] Message: " + e);
            return 1;
        } catch (NoJavaCompilerException e) {
            System.err.println("[MAIN    ] Error: Failed to find a system Java compiler. Did you install a JDK?");
            return 1;
        } catch (InterruptedException e) {
            System.err.println("[MAIN    ] Internal error: Unexpected interruption when waiting for termination of application");
            System.err.println("[MAIN    ] Message: " + e);
            return 2;
        } catch (NullPointerException e) {
            System.err.println("[MAIN    ] Internal error: Unexpected null value");
            System.err.println("[MAIN    ] Message: " + e);
            return 2;
        } catch (IllegalArgumentException e) {
            System.err.println("[MAIN    ] Internal error: Unexpected illegal argument");
            System.err.println("[MAIN    ] Message: " + e);
            return 2;
        } catch (JavaCompilerException e) {
            System.err.println("[MAIN    ] Internal error: Unexpected I/O error while creating test case compilation log file");
            System.err.println("[MAIN    ] Message: " + e);
            return 2;
        }
    }

    /**
     * Generates a seed for the Evosuite performer, 
     * @return
     * @throws SecurityException
     */
    private ArrayList<JBSEResult> generateSeedForPerformerEvosuite() throws SecurityException {
        //this is the "no initial test case" situation
        final ArrayList<JBSEResult> retVal = new ArrayList<>();
        if (this.o.getTargetMethod() == null) {
            retVal.add(new JBSEResult(this.o.getTargetClass()));
        } else {
            retVal.add(new JBSEResult(this.o.getTargetMethod()));
        }
        return retVal;
    }

    private ArrayList<EvosuiteResult> generateSeedForPerformerJBSE() throws NoJavaCompilerException, JavaCompilerException {
        final TestCase tc = new TestCase(this.o, false);
        final String classpathCompilationTest = String.join(File.pathSeparator, stream(this.o.getClassesPath()).map(Object::toString).toArray(String[]::new));
        final Path javacLogFilePath = this.o.getTmpDirectoryPath().resolve("javac-log-test-0.txt");
        final String[] javacParametersTestCase = { "-cp", classpathCompilationTest, "-d", this.o.getTmpBinDirectoryPath().toString(), tc.getSourcePath().toString() };
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new NoJavaCompilerException();
        }
        try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
            compiler.run(null, w, w, javacParametersTestCase);
        } catch (IOException e) {
            throw new JavaCompilerException(javacLogFilePath.toString(), e);
        }
        final ArrayList<EvosuiteResult> retVal = new ArrayList<>();
        retVal.add(new EvosuiteResult(this.o.getTargetMethod(), tc, 0));
        return retVal;
    }
    
    /**
     * Returns the name of this application, as resulting
     * from the containing jar file.
     * 
     * @return a {@link String} or {@code null} if this 
     *         class is not packaged in a jar file.
     */
    public static String getName() {
        return Main.class.getPackage().getImplementationTitle();
    }

    /**
     * Returns the vendor of this application, as resulting
     * from the containing jar file.
     * 
     * @return a {@link String} or {@code null} if this 
     *         class is not packaged in a jar file.
     */
    public static String getVendor() {
        return Main.class.getPackage().getImplementationVendor();
    }

    /**
     * Returns the version of this application, as resulting
     * from the containing jar file.
     * 
     * @return a {@link String} or {@code null} if this 
     *         class is not packaged in a jar file.
     */
    public static String getVersion() {
        return Main.class.getPackage().getImplementationVersion();
    }


    //Here starts the static part of the class, for managing the command line

    public static void main(String[] args) {		
        //parses options from the command line and exits if the command line
        //is ill-formed
        final Options o = new Options();
        final CmdLineParser parser = new CmdLineParser(o, ParserProperties.defaults().withUsageWidth(200));
        try {
            parser.parseArgument(processArgs(args));
        } catch (CmdLineException e) {
            System.err.println("Error: " + e.getMessage());
            printUsage(parser);
            System.exit(1);
        }

        //prints help and exits if asked to
        if (o.getHelp()) {
            printUsage(parser);
            System.exit(0);
        }

        //runs
        final Main m = new Main(o);
        final int exitCode = m.start();
        System.exit(exitCode);
    }

    /**
     * Processes the command line arguments so they
     * can be parsed by the command line parser.
     * 
     * @param args the {@link String}{@code []} from the command line.
     * @return a processed {@link String}{@code []}.
     */
    private static String[] processArgs(final String[] args) {
        final Pattern argPattern = Pattern.compile("(-[a-zA-Z_-]+)=(.*)");
        final Pattern quotesPattern = Pattern.compile("^['\"](.*)['\"]$");
        final List<String> processedArgs = new ArrayList<String>();

        for (String arg : args) {
            final Matcher matcher = argPattern.matcher(arg);
            if (matcher.matches()) {
                processedArgs.add(matcher.group(1));
                final String value = matcher.group(2);
                final Matcher quotesMatcher = quotesPattern.matcher(value);
                if (quotesMatcher.matches()) {
                    processedArgs.add(quotesMatcher.group(1));
                } else {
                    processedArgs.add(value);
                }
            } else {
                processedArgs.add(arg);
            }
        }

        return processedArgs.toArray(new String[0]);
    }

    /**
     * Prints usage on the standard error.
     * 
     * @param parser a {@link CmdLineParser}.
     */
    private static void printUsage(final CmdLineParser parser) {
        System.err.println("Usage: java " + Main.class.getName() + " <options>");
        System.err.println("where <options> are:");
        parser.printUsage(System.err);
    }
}