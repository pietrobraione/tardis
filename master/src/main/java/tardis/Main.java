package tardis;

import static java.nio.file.Files.createDirectory;
import static java.nio.file.Files.exists;

import static jbse.bc.ClassLoaders.CLASSLOADER_APP;
import static tardis.implementation.Util.getUniqueTargetMethod;
import static tardis.implementation.Util.getVisibleTargetMethods;
import static tardis.implementation.Util.stream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

import jbse.bc.ClassFile;
import jbse.bc.ClassFileFactoryJavassist;
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
import jbse.bc.exc.WrongClassNameException;
import jbse.common.exc.InvalidInputException;
import jbse.mem.State;
import jbse.mem.exc.CannotAssumeSymbolicObjectException;
import jbse.mem.exc.HeapMemoryExhaustedException;
import jbse.rewr.CalculatorRewriting;
import jbse.rewr.RewriterOperationOnSimplex;
import jbse.val.HistoryPoint;
import jbse.val.SymbolFactory;

import tardis.framework.TerminationManager;
import tardis.implementation.CoverageSet;
import tardis.implementation.EvosuiteResult;
import tardis.implementation.JBSEResult;
import tardis.implementation.PerformerEvosuite;
import tardis.implementation.PerformerJBSE;
import tardis.implementation.QueueInputOutputBuffer;
import tardis.implementation.TestCase;
import tardis.implementation.Util;

public final class Main {
	private final Options o;
	
	public Main(Options o) {
		this.o = o;
	}
	
	public void start() throws IOException {
		//creates the temporary directories if it does not exist
		try {
			if (!exists(o.getTmpDirectoryPath())) {
				createDirectory(o.getTmpDirectoryPath());
			}
			if (!exists(o.getTmpBinTestsDirectoryPath())) {
				createDirectory(o.getTmpBinTestsDirectoryPath());
			}
		} catch (IOException e) {
			System.out.println("[MAIN    ] Error: unable to create temporary directories, does the base directory exist?");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(1);
		}
		
		//creates the coverage data structure
		final CoverageSet coverageSet = new CoverageSet();
		
		//creates the communication queues between the performers
		final QueueInputOutputBuffer<JBSEResult> pathConditionBuffer = new QueueInputOutputBuffer<>();
		final QueueInputOutputBuffer<EvosuiteResult> testCaseBuffer = new QueueInputOutputBuffer<>();
		
		//creates and wires together the components of the architecture
		final PerformerJBSE performerJBSE = new PerformerJBSE(this.o, testCaseBuffer, pathConditionBuffer, coverageSet);
		final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
		final TerminationManager terminationManager = new TerminationManager(this.o.getGlobalTimeBudgetDuration(), this.o.getGlobalTimeBudgetUnit(), performerJBSE, performerEvosuite);
		
		//seeds the initial test cases
		if (this.o.getTargetMethod() == null || this.o.getInitialTestCase() == null) {
			//the target is a whole class, or is a single method but
			//there is no initial test case: EvoSuite should start
			final ArrayList<JBSEResult> seed = seedForEvosuite();
			performerEvosuite.seed(seed);
		} else {
			//the target is a single method and there is one
			//initial test case: JBSE should start
			final ArrayList<EvosuiteResult> seed = seedForJBSE();
			performerJBSE.seed(seed);
		}
		
		//starts everything
		System.out.println("[MAIN    ] This is " + Util.getName() + ", version " + Util.getVersion() + ", " + '\u00a9' + " 2017-2019 " + Util.getVendor());
		final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		System.out.println("[MAIN    ] Starting at " + dtf.format(LocalDateTime.now()));
		performerJBSE.start();
		performerEvosuite.start();
		terminationManager.start();
		
		//waits end and prints a final message
		terminationManager.waitTermination();
		System.out.println("[MAIN    ] Ending at " + dtf.format(LocalDateTime.now()));
	}
	
	private ArrayList<JBSEResult> seedForEvosuite() {
		//this is the "no initial test case" situation
		try {
			final CalculatorRewriting calc = new CalculatorRewriting();
			calc.addRewriter(new RewriterOperationOnSimplex());
			final ArrayList<JBSEResult> retVal = new ArrayList<>();
			final List<List<String>> targetMethods;
			if (this.o.getTargetMethod() == null) {
				//this.o indicates a target class
				targetMethods = getVisibleTargetMethods(this.o);
			} else {
				//this.o indicates a single target method
				targetMethods = getUniqueTargetMethod(this.o);
			}
			for (List<String> targetMethod : targetMethods) {
				final State s = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, this.o.getClasspath(), ClassFileFactoryJavassist.class, new HashMap<>(), calc, new SymbolFactory(calc));
				final ClassFile cf = s.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, targetMethod.get(0), true);
				s.pushFrameSymbolic(cf, new Signature(targetMethod.get(0), targetMethod.get(1), targetMethod.get(2)));
				retVal.add(new JBSEResult(targetMethod.get(0), targetMethod.get(1), targetMethod.get(2), s, s, s, false, Collections.emptyMap(), -1));
			}
			return retVal;
		} catch (ClassNotFoundException | WrongClassNameException | BadClassFileVersionException | ClassFileNotFoundException | IncompatibleClassFileException | 
			     ClassFileNotAccessibleException | ClassFileIllFormedException | MethodNotFoundException | MethodCodeNotFoundException e) {
			System.out.println("[MAIN    ] Error: The target class or target method has wrong name, or version, or does not exist, or has unaccessible hierarchy, or is ill-formed, or the target method is abstract");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(1);
		} catch (MalformedURLException e) {
			System.out.println("[MAIN    ] Error: A path in the specified classpath does not exist or is ill-formed");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(1);
		} catch (IOException e) {
			System.out.println("[MAIN    ] Error: I/O exception while accessing the classpath");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(1);
		} catch (SecurityException e) {
			System.out.println("[MAIN    ] Error: The security manager did not allow to get the system class loader");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(1);
		} catch (CannotAssumeSymbolicObjectException e) {
			System.out.println("[MAIN    ] Error: Cannot execute symbolically a method of class java.lang.Class or java.lang.ClassLoader");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(1);
		} catch (InvalidInputException e) {
			System.out.println("[MAIN    ] Unexpected internal error: Invalid parameter");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(2);
		} catch (PleaseLoadClassException e) {
			System.out.println("[MAIN    ] Unexpected internal error: Class loading failed");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(2);
		} catch (HeapMemoryExhaustedException e) {
			System.out.println("[MAIN    ] Unexpected internal error: Heap memory exhausted");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(2);
		} catch (InvalidClassFileFactoryClassException e) {
			System.out.println("[MAIN    ] Unexpected internal error: Wrong class file factory");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(2);
		}
		return null; //to keep the compiler happy
	}
	
	private ArrayList<EvosuiteResult> seedForJBSE() {
		final TestCase tc = new TestCase(this.o);
		final String classpathCompilationTest = String.join(File.pathSeparator, stream(this.o.getClassesPath()).map(Object::toString).toArray(String[]::new));
		final Path javacLogFilePath = this.o.getTmpDirectoryPath().resolve("javac-log-test-0.txt");
		final String[] javacParametersTestCase = { "-cp", classpathCompilationTest, "-d", this.o.getTmpBinTestsDirectoryPath().toString(), tc.getSourcePath().toString() };
		final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			System.out.println("[MAIN    ] Failed to find a system Java compiler. Did you install a JDK?");
			System.exit(1);
		}
		try (final OutputStream w = new BufferedOutputStream(Files.newOutputStream(javacLogFilePath))) {
			compiler.run(null, w, w, javacParametersTestCase);
		} catch (IOException e) {
			System.out.println("[MAIN    ] Unexpected I/O error while creating test case compilation log file " + javacLogFilePath.toString());
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(2);
		}
		final ArrayList<EvosuiteResult> retVal = new ArrayList<>();
		retVal.add(new EvosuiteResult(this.o.getTargetMethod().get(0), this.o.getTargetMethod().get(1), this.o.getTargetMethod().get(2), tc, 0));
		return retVal;
	}
	
	//Here starts the static part of the class, for managing the command line
	
	public static void main(String[] args) throws IOException {		
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
		m.start();
	}

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

	private static void printUsage(final CmdLineParser parser) {
		System.err.println("Usage: java " + Main.class.getName() + " <options>");
		System.err.println("where <options> are:");
		// print the list of available options
		parser.printUsage(System.err);
	}
}