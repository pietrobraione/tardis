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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

import tardis.framework.TerminationManager;
import tardis.implementation.EvosuiteResult;
import tardis.implementation.JBSEResult;
import tardis.implementation.JBSEResultInputOutputBuffer;
import tardis.implementation.NoJavaCompilerException;
import tardis.implementation.PerformerEvosuite;
import tardis.implementation.PerformerEvosuiteInitException;
import tardis.implementation.PerformerJBSE;
import tardis.implementation.QueueInputOutputBuffer;
import tardis.implementation.TestCase;
import tardis.implementation.TreePath;

/**
 * TARDIS main class.
 * 
 * @author Pietro Braione
 */
public final class Main {
    private static Logger LOGGER;
    
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
        configureLogger();
        LOGGER = LogManager.getFormatterLogger(Main.class);
        
        try {
            //creates the temporary directories if they do not exist
            if (!exists(o.getTmpDirectoryPath())) {
                createDirectory(o.getTmpDirectoryPath());
            }
            if (!exists(o.getTmpBinDirectoryPath())) {
                createDirectory(o.getTmpBinDirectoryPath());
            }

            //creates the state space and coverage data structure
            final TreePath treePath = new TreePath();

            //creates the communication buffers between the performers
            final JBSEResultInputOutputBuffer pathConditionBuffer = new JBSEResultInputOutputBuffer(treePath);
            final QueueInputOutputBuffer<EvosuiteResult> testCaseBuffer = new QueueInputOutputBuffer<>();

            //creates and wires together the components of the architecture
            final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
            final PerformerJBSE performerJBSE = new PerformerJBSE(this.o, testCaseBuffer, pathConditionBuffer, treePath);
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
            
            //logs some message
            LOGGER.info("This is %s, version %s, \u00a9 2017-2021 %s", getName(), getVersion(), getVendor());
            LOGGER.info("Target is %s", (this.o.getTargetMethod() == null ? ("class " + this.o.getTargetClass()) : ("method " + this.o.getTargetMethod().get(0) + ":" + this.o.getTargetMethod().get(1) + ":" + this.o.getTargetMethod().get(2))));
            
            //starts everything
            performerJBSE.start();
            performerEvosuite.start();
            terminationManager.start();

            //waits end
            terminationManager.waitTermination();
            
            //prints a final message and returns
            LOGGER.info("%s ends", getName());
            return 0;
        } catch (IOException e) {
            LOGGER.error("Unexpected I/O error");
            LOGGER.error("Message: %s", e);
            return 1;
        } catch (PerformerEvosuiteInitException e) {
            LOGGER.error("Unable to create the Evosuite performer");
            LOGGER.error("Message: %s", e);
            return 1;
        } catch (NoJavaCompilerException e) {
            LOGGER.error("Failed to find a system Java compiler. Did you install a JDK?");
            return 1;
        } catch (JavaCompilerException e) {
            LOGGER.error("Internal error: Unexpected I/O error while creating test case compilation log file");
            LOGGER.error("Message: %s", e);
            return 2;
        } catch (InterruptedException e) {
            LOGGER.error("Internal error: Unexpected interruption when waiting for termination of application");
            LOGGER.error("Message: %s", e);
            return 2;
        } catch (RuntimeException e) {
            LOGGER.error("Internal error: Unexpected runtime exception");
            LOGGER.error("Message: %s", e);
            return 2;
        }
    }
    
    /**
     * Configures the logger
     */
    private void configureLogger() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.WARN);

        //appender
        AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE");
        appenderBuilder.addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
        LayoutComponentBuilder layoutBuilder = builder.newLayout("PatternLayout");
        layoutBuilder.addAttribute("pattern", "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n");
        appenderBuilder.add(layoutBuilder);
        builder.add(appenderBuilder);

        //root logger
        RootLoggerComponentBuilder rootLoggerBuilder = builder.newRootLogger(this.o.getVerbosity());
        rootLoggerBuilder.add(builder.newAppenderRef("Stdout"));
        builder.add(rootLoggerBuilder);
        Configurator.initialize(builder.build());
    }


    /**
     * Generates a seed for the Evosuite performer.
     * 
     * @return an {@link ArrayList}{@code <}{@link JBSEResult}{@code >}, to be
     *         inserted in the input queue of the Evosuite performed.
     */
    private ArrayList<JBSEResult> generateSeedForPerformerEvosuite() {
        //this is the "no initial test case" situation
        final ArrayList<JBSEResult> retVal = new ArrayList<>();
        if (this.o.getTargetMethod() == null) {
            retVal.add(new JBSEResult(this.o.getTargetClass()));
        } else {
            retVal.add(new JBSEResult(this.o.getTargetMethod()));
        }
        return retVal;
    }

    private ArrayList<EvosuiteResult> generateSeedForPerformerJBSE() 
    throws NoJavaCompilerException, JavaCompilerException {
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