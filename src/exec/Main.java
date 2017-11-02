package exec;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

import concurrent.TerminationManager;
import jbse.bc.ClassFileFactoryJavassist;
import jbse.bc.Classpath;
import jbse.bc.Signature;
import jbse.bc.exc.BadClassFileException;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.bc.exc.MethodCodeNotFoundException;
import jbse.bc.exc.MethodNotFoundException;
import jbse.mem.State;
import jbse.rewr.CalculatorRewriting;
import jbse.rewr.RewriterOperationOnSimplex;

public final class Main {
	private final Options o;
	
	public Main(Options o) {
		this.o = o;
	}
	
	public void start() {
		//creates the communication queues between the performers
		final QueueInputOutputBuffer<JBSEResult> pathConditionBuffer = new QueueInputOutputBuffer<>();
		final QueueInputOutputBuffer<EvosuiteResult> testCaseBuffer = new QueueInputOutputBuffer<>();
		
		//creates and wires the components of the architecture
		final PerformerJBSE performerJBSE = new PerformerJBSE(this.o, testCaseBuffer, pathConditionBuffer);
		final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
		final TerminationManager terminationManager = new TerminationManager(this.o.getGlobalTimeBudgetDuration(), this.o.getGlobalTimeBudgetUnit(), performerJBSE, performerEvosuite);
		
		//sets logging
		final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		terminationManager.setOnStop(() -> {
			System.out.println("[MAIN    ] Ending at " + dtf.format(LocalDateTime.now()));
		}); 
		
		//seeds the EvoSuite performer with the initial test case
		final JBSEResult seed = seed();
		if (seed == null) {
			System.exit(1);
		}
		performerEvosuite.seed(seed);
		
		//starts everything
		System.out.println("[MAIN    ] Starting at " + dtf.format(LocalDateTime.now()));
		performerJBSE.start();
		performerEvosuite.start();
		terminationManager.start();
	}
	
	private JBSEResult seed() {
		try {
			final String[] classpath = new String[3];
			classpath[0] = this.o.getBinPath().toString();
			classpath[1] = this.o.getJBSELibraryPath().toString();
			classpath[2] = this.o.getJREPath().toString();
			final CalculatorRewriting calc = new CalculatorRewriting();
			calc.addRewriter(new RewriterOperationOnSimplex());
			final State s = new State(new Classpath(classpath), ClassFileFactoryJavassist.class, new HashMap<>(), calc);
			s.pushFrameSymbolic(new Signature(this.o.getTargetMethod().get(0), this.o.getTargetMethod().get(1), this.o.getTargetMethod().get(2)));
			final JBSEResult retVal = new JBSEResult(s, s, -1);
			return retVal;
		} catch (BadClassFileException | MethodNotFoundException | MethodCodeNotFoundException e) {
			System.out.println("[MAIN    ] Error: The target class or target method does not exist, or the target method is abstract");
		} catch (InvalidClassFileFactoryClassException e) {
			System.out.println("[MAIN    ] Unexpected internal error: Wrong class file factory");
		}
		return null;
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