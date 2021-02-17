package tardis;

import static java.nio.file.Files.createDirectory;
import static java.nio.file.Files.exists;

import static tardis.implementation.Util.stream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.lang.model.SourceVersion;
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
import tardis.implementation.NoJava8JVMException;
import tardis.implementation.NoJava8ToolsJarException;
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
    /**
     * The logger.
     */
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
            //logs some message
            LOGGER.info("This is %s, version %s, \u00a9 2017-2021 %s", getName(), getVersion(), getVendor());
            LOGGER.info("Target is %s", (this.o.getTargetMethod() == null ? ("class " + this.o.getTargetClass()) : ("method " + this.o.getTargetMethod().get(0) + ":" + this.o.getTargetMethod().get(1) + ":" + this.o.getTargetMethod().get(2))));
            
            //checks prerequisites
            checkPrerequisites();
            
            //creates the temporary directories if they do not exist
            createTmpDirectories();

            //creates and wires together the components of the architecture: 
            //the TreePath...
            final TreePath treePath = new TreePath();

            //...the communication buffers...
            final JBSEResultInputOutputBuffer pathConditionBuffer = new JBSEResultInputOutputBuffer(treePath);
            final QueueInputOutputBuffer<EvosuiteResult> testCaseBuffer = new QueueInputOutputBuffer<>();

            //...the performers and the termination manager
            final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
            final PerformerJBSE performerJBSE = new PerformerJBSE(this.o, testCaseBuffer, pathConditionBuffer, treePath);
            final TerminationManager terminationManager = new TerminationManager(this.o.getGlobalTimeBudgetDuration(), this.o.getGlobalTimeBudgetUnit(), performerJBSE, performerEvosuite);

            //seeds a performer to bootstrap
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
            performerJBSE.start();
            performerEvosuite.start();
            terminationManager.start();

            //waits for the end
            terminationManager.waitTermination();
            
            //logs a final message and returns
            LOGGER.info("%s ends", getName());
            return 0;
        } catch (NoJavaCompilerException e) {
            LOGGER.error("Failed to find a system Java compiler for Java version 8.");
            return 1;
        } catch (NoJava8JVMException e) {
            LOGGER.error("Failed to find a Java version 8 JDK (no JVM).");
            return 1;
        } catch (NoJava8ToolsJarException e) {
            LOGGER.error("Failed to find a Java version 8 JDK (no tools.jar).");
            return 1;
        } catch (PerformerEvosuiteInitException e) {
            LOGGER.error("Unable to create the Evosuite performer");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
            return 1;
        } catch (IOException e) {
            LOGGER.error("Unexpected I/O error");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
            return 1;
        } catch (JavaCompilerException e) {
            LOGGER.error("Internal error: Unexpected I/O error while creating test case compilation log file");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
            return 2;
        } catch (InterruptedException e) {
            LOGGER.error("Internal error: Unexpected interruption when waiting for termination of application");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
            return 2;
        } catch (RuntimeException e) {
            LOGGER.error("Internal error: Unexpected runtime exception");
            LOGGER.error("Message: %s", e.toString());
            LOGGER.error("Stack trace:");
            for (StackTraceElement elem : e.getStackTrace()) {
                LOGGER.error("%s", elem.toString());
            }
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
     * Checks prerequisites.
     * 
     * @throws NoJavaCompilerException if no Java compiler is installed.  
     * @throws NoJava8JVMException if no Java 8 JVM is installed.
     * @throws NoJava8ToolsJarException if no Java 8 tools.jar is installed.
     */
    private void checkPrerequisites() throws NoJavaCompilerException, IOException, InterruptedException, NoJava8JVMException, NoJava8ToolsJarException {
        //looks for a Java compiler for version 8 source files
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new NoJavaCompilerException();
        }
        if (!compiler.getSourceVersions().contains(SourceVersion.RELEASE_8)) {
            throw new NoJavaCompilerException();
        }
        
        //looks for a Java version 8 JDK (JVM)
        final ArrayList<String> commandLine = new ArrayList<>();
        if (this.o.getJava8Home() == null) {
            commandLine.add("java");
        } else {
            commandLine.add(this.o.getJava8Home().resolve("bin/java").toAbsolutePath().toString());
        }
        commandLine.add("-version");
        final ProcessBuilder pb = new ProcessBuilder(commandLine).redirectErrorStream(true);
        final Process pr = pb.start();
        final InputStream prInput = pr.getInputStream();
        final StringBuilder buf = new StringBuilder();
        int datum;
        while ((datum = prInput.read()) != -1) {
            buf.append((char) datum);
        }
        pr.waitFor();
        final int first = buf.indexOf("\"");
        final int last = buf.indexOf("\"", first + 1);
        if (first == -1 || last == -1 || !buf.subSequence(first + 1, last).toString().startsWith("1.8.")) {
            throw new NoJava8JVMException();
        }
        
        //looks for a Java version 8 JDK (tools.jar)
        final Path toolsJar = Paths.get(System.getProperty("java.home", "")).resolve("../lib/tools.jar");
        if (!exists(toolsJar)) {
            throw new NoJava8ToolsJarException();
        }
    }
    
    /**
     * Creates the temporary directories.
     * 
     * @throws IOException if some I/O error occurs during
     *         directory creation.
     */
    private void createTmpDirectories() throws IOException {
        if (!exists(this.o.getTmpDirectoryPath())) {
            createDirectory(this.o.getTmpDirectoryPath());
        }
        if (!exists(this.o.getTmpBinDirectoryPath())) {
            createDirectory(this.o.getTmpBinDirectoryPath());
        }
    }

    /**
     * Generates a seed for the Evosuite performer.
     * 
     * @return an {@link ArrayList}{@code <}{@link JBSEResult}{@code >}, to be
     *         inserted in the input queue of the Evosuite performer.
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

    /**
     * Generates a seed for the JBSE performer.
     * 
     * @return an {@link ArrayList}{@code <}{@link EvosuiteResult}{@code >}, to be
     *         inserted in the input queue of the JBSE performer.
     * @throws NoJavaCompilerException if no Java compiler is installed.
     */
    private ArrayList<EvosuiteResult> generateSeedForPerformerJBSE() 
    throws NoJavaCompilerException, JavaCompilerException {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new NoJavaCompilerException();
        }
        final TestCase tc = new TestCase(this.o, false);
        final String classpathCompilationTest = String.join(File.pathSeparator, stream(this.o.getClassesPath()).map(Object::toString).toArray(String[]::new));
        final Path javacLogFilePath = this.o.getTmpDirectoryPath().resolve("javac-log-test-0.txt");
        final String[] javacParametersTestCase = { "-cp", classpathCompilationTest, "-d", this.o.getTmpBinDirectoryPath().toString(), tc.getSourcePath().toString() };
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