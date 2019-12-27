
package tardis;

import static java.nio.file.Files.createDirectory;
import static java.nio.file.Files.exists;
import static jbse.bc.ClassLoaders.CLASSLOADER_APP;
import static tardis.implementation.Util.getUniqueTargetMethod;
import static tardis.implementation.Util.getVisibleTargetMethods;
import static tardis.implementation.Util.stream;
import static tardis.implementation.Util.stringifyPathCondition;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.Statement;

import jbse.algo.exc.CannotManageStateException;
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
import jbse.common.exc.ClasspathException;
import jbse.common.exc.InvalidInputException;
import jbse.dec.exc.DecisionException;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.mem.Clause;
import jbse.mem.ClauseAssumeClassInitialized;
import jbse.mem.ClauseAssumeClassNotInitialized;
import jbse.mem.State;
import jbse.mem.exc.CannotAssumeSymbolicObjectException;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.HeapMemoryExhaustedException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.val.HistoryPoint;
import jbse.val.SymbolFactory;
import parser.DirExplorer;
import parser.NodeIterator;
import tardis.framework.TerminationManager;
import tardis.implementation.EvosuiteResult;
import tardis.implementation.JBSEResult;
import tardis.implementation.PerformerEvosuite;
import tardis.implementation.QueueInputOutputBuffer;
import tardis.implementation.RunnerPath;
import tardis.implementation.TestCase;
import tree.implementation.TreePath;



public final class Main {
	private final Options o;

	public Main(Options o) {
		this.o = o;
	}

	public void start() throws IOException {	
		
		o.setEvosuiteTimeBudgetDuration(20);
		
		//creates the temporary directories if it does not exist
		try {
			if (!exists(o.getTmpDirectoryPath())) {
				createDirectory(o.getTmpDirectoryPath());
			}
			if (!exists(o.getTmpBinDirectoryPath())) {
				createDirectory(o.getTmpBinDirectoryPath());
			}
		} catch (IOException e) {
			System.out.println("[MAIN    ] Error: unable to create temporary directories, does the base directory exist?");
			System.out.println("[MAIN    ] Message: " + e);
			System.exit(1);
		}


		//creates the communication queues between the performers
		final QueueInputOutputBuffer<JBSEResult> pathConditionBuffer = new QueueInputOutputBuffer<>();
		final QueueInputOutputBuffer<EvosuiteResult> testCaseBuffer = new QueueInputOutputBuffer<>();

		//creates and wires together the components of the architecture

		final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
		final TerminationManager terminationManager = new TerminationManager(this.o.getGlobalTimeBudgetDuration(), this.o.getGlobalTimeBudgetUnit(),performerEvosuite);

		final ArrayList<JBSEResult> seed = seedForEvosuite();
		performerEvosuite.seed(seed);

		final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

		System.out.println("[MAIN    ] Starting at " + dtf.format(LocalDateTime.now()) + ", target is " + (this.o.getTargetMethod() == null ? ("class " + this.o.getTargetClass()) : ("method " + this.o.getTargetMethod().get(0) + ":" + this.o.getTargetMethod().get(1) + ":" + this.o.getTargetMethod().get(2))));

		Path out = o.getOutDirectory();

		//cancella tutti i file (di esecuzioni precedenti) che erano presenti nella cartella
		deleteFiles(out + "\\testgen");

		performerEvosuite.start();
		terminationManager.start();

		//waits end and prints a final message
		terminationManager.waitTermination();

		File projectDir = new File(out + "\\testgen");
		System.out.println("Splitting ... ");
		multipleEvosuiteTests(projectDir);
		
		//cancella il file con tutti i metodi creati da evosuite

		File evo = new File ("C:\\Users\\Flora\\Desktop\\TARDIS\\tardis-experiments\\tardis-test\\testgen\\Testgen_ESTest.java");
		evo.delete();

		System.out.println("[Creating Evosuite Files  ] Ending at " + dtf.format(LocalDateTime.now()));

		try {
			JBSE(o);
		} catch (DecisionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CannotBuildEngineException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InitializationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidClassFileFactoryClassException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NonexistingObservedVariablesException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClasspathException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ContradictionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CannotBacktrackException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CannotManageStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ThreadStackEmptyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (EngineStuckException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FailureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static int count ;
	static TreePath treePath = new TreePath();

	public static void JBSE(Options o) throws DecisionException, CannotBuildEngineException, InitializationException, InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, ClasspathException, ContradictionException, CannotBacktrackException, CannotManageStateException, ThreadStackEmptyException, EngineStuckException, FailureException, IOException	{
		
		try (Stream<Path> walk = Files.walk(Paths.get("C:\\Users\\Flora\\Desktop\\TARDIS\\tardis-experiments\\tardis-test\\testgen"))) {

			List<String> result = walk.map(x -> x.toString())
					.filter(f -> (f.endsWith("_Test.java") )).collect(Collectors.toList());
			count = result.size();
			System.out.println(count);
		} 
		catch (IOException e) {
			e.printStackTrace();
		}
		
	//	final Options o = new Options();
		/*o.setTmpDirectoryBase(Settings.TMP_BASE_PATH);
		o.setZ3Path(Settings.Z3_PATH);
		o.setJBSELibraryPath(Settings.JBSE_PATH);
		o.setClassesPath(Settings.BIN_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_MOSA_PATH);*/
		
		o.setInitialTestCasePath(Settings.OUT_PATH);

		final String targetClass = "testgen/Testgen";
		final String targetMethodDescriptor = "(Ltestgen/Testgen$Node;I)Ltestgen/Testgen$Node;";
		final String targetMethodName  = "getNode";
		final String tcTestMethodDescriptor = "()V";
		final String tcTestMethodName = "test0";

		for (int k = 1 ; k <= count ; k++) {
			//creates an EvosuiteResult object
			final String tcTestClass = "testgen/Testgen_" + k + "_Test";
			final TestCase tc = new TestCase(tcTestClass,  tcTestMethodDescriptor, tcTestMethodName, Settings.OUT_PATH) ;
			final EvosuiteResult e = new EvosuiteResult(targetClass,  targetMethodDescriptor, targetMethodName, tc , 0);

			//runs the test
			final RunnerPath rp = new RunnerPath(o, e);
			final State tcFinalState = rp.runProgram();
			final Collection<Clause> tcFinalPC = tcFinalState.getPathCondition();
			addCovered(tcFinalPC);

			System.out.println("[JBSE    ] Test case " + tc.getClassName() + " has path condition " + stringifyPathCondition(shorten(tcFinalPC)));

			final int tcFinalDepth = tcFinalState.getDepth();

			boolean noPathConditionGenerated = true;
			for (int currentDepth = 1 ; currentDepth <= tcFinalDepth ; ++currentDepth) {
				//runs the program
				final List<State> newStates = rp.runProgram(currentDepth);
				final State initialState = rp.getInitialState();
				final State preState = rp.getPreState();
				final boolean atJump = rp.getAtJump();
				final List<String> targetBranches = rp.getTargetBranches(); 
				final Map<Long, String> stringLiterals = rp.getStringLiterals();
				for (int i = 0; i < newStates.size(); ++i) {
					final State newState = newStates.get(i);
					final Collection<Clause> currentPC = newState.getPathCondition();
					if (alreadyExplored(currentPC)) {
						continue;
					}
					final JBSEResult item = new JBSEResult(e, initialState, preState, newState, atJump, (atJump ? targetBranches.get(i) : null), stringLiterals, currentDepth);
					addItem(currentPC, item);
					noPathConditionGenerated = false;
				}
			}
			if (noPathConditionGenerated) {
			}

		}

		//emits the result
		for (JBSEResult r : treePath.getItems()) {
			System.out.println("[JBSE    ] Generated path condition " + stringifyPathCondition(shorten(r.getFinalState().getPathCondition())));
		}

	}

	private static boolean alreadyExplored(Collection<Clause> newPC) {
		return treePath.containsPath(newPC);
	}

	private static void addCovered(Collection<Clause> newPC) {
		treePath.insertAndCleanPath(newPC);
	}

	private static void addItem(Collection<Clause> newPC, JBSEResult item) {
		treePath.insertPath(newPC, item);
	}

	static Collection<Clause> shorten(Collection<Clause> pc) {
		return pc.stream().filter(x -> !(x instanceof ClauseAssumeClassInitialized || x instanceof ClauseAssumeClassNotInitialized)).collect(Collectors.toList());
	}

	private void deleteFiles(String dir) {	
		File directory = new File(dir);
		File[] files = directory.listFiles();
		for (File f : files)
			f.delete();
	}

	private static String importsToString = "" ;

	private void multipleEvosuiteTests(File projectDir) {
		new DirExplorer((level, path, file) -> (path.endsWith(".java") && !path.endsWith("_scaffolding.java") ), (level, path, file) -> {

			try {
				//package
				importsToString =  importsToString.concat(JavaParser.parse(file).getPackage().toString());
				if (!JavaParser.parse(file).getImports().isEmpty() ) {
					Object[]  importDeclarationList = JavaParser.parse(file).getImports().toArray();

					for (Object o : importDeclarationList)
						//import
						importsToString = importsToString.concat(o.toString()); }
			} catch (ParseException | IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			try {
				new NodeIterator(new NodeIterator.NodeHandler() {
					int c = 0 ;
					@Override
					public boolean handle(Node node) {

						if (node instanceof Statement) {
							c++;
							String FilePath = "C:\\Users\\Flora\\Desktop\\TARDIS\\tardis-experiments\\tardis-test\\testgen\\Testgen_" + c + "_Test.java" ;
							File file = new File(FilePath);
							try {
								if(file.createNewFile()){
									System.out.println("File \"" + file.getName() +"\" Created");
								}
								else System.out.println("File "+ file.getName() +" already exists");
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							BufferedWriter writer;
							try {
								writer = new BufferedWriter(new FileWriter(file));
								writer.write(importsToString + "\n\n");
								writer.write("@RunWith(EvoRunner.class) @EvoRunnerParameters(mockJVMNonDeterminism = true, useVNET = true, separateClassLoader = true, useJEE = true)" + "\n") ;
								writer.write("public class Testgen_" + c +"_Test extends Testgen_ESTest_scaffolding {" + "\n\n" +
										"   @Test(timeout = 4000) " +"\n" +
										"   public void test0()  throws Throwable " +"\n\n" +
										node.toString()+ "\n" + "}");
								writer.close();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();}
							return false;
						} else {
							return true;
						}
					}
				}).explore(JavaParser.parse(file));
			} catch (ParseException | IOException e) {
				new RuntimeException(e);
			}
		}).explore(projectDir);
	}


	private ArrayList<JBSEResult> seedForEvosuite() {
		//this is the "no initial test case" situation
		try {
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
				final State s = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, this.o.getClasspath(), ClassFileFactoryJavassist.class, new HashMap<>(), new SymbolFactory());
				final ClassFile cf = s.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, targetMethod.get(0), true);
				s.pushFrameSymbolic(cf, new Signature(targetMethod.get(0), targetMethod.get(1), targetMethod.get(2)));
				retVal.add(new JBSEResult(targetMethod.get(0), targetMethod.get(1), targetMethod.get(2), s, s, s, false, null, Collections.emptyMap(), -1));
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
		final String[] javacParametersTestCase = { "-cp", classpathCompilationTest, "-d", this.o.getTmpBinDirectoryPath().toString(), tc.getSourcePath().toString() };
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
