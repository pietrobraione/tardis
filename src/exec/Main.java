package exec;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.ParserProperties;

import concurrent.TerminationManager;

public final class Main {
	private final Options o;
	
	public Main(Options o) {
		this.o = o;
	}
	
	public void start() {
		//creates the communication queues between the performers
		final QueueInputOutputBuffer<JBSEResult> pathConditionBuffer = new QueueInputOutputBuffer<>();
		final QueueInputOutputBuffer<EvosuiteResult> testCaseBuffer = new QueueInputOutputBuffer<>();
		
		//seeds testCaseBuffer with the initial test case
		final TestCase tc = new TestCase(this.o);
		final EvosuiteResult item = new EvosuiteResult(tc, 0);
		testCaseBuffer.add(item);
		
		//creates and wires the components of the architecture
		final PerformerJBSE performerJBSE = new PerformerJBSE(this.o, testCaseBuffer, pathConditionBuffer);
		final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
		final TerminationManager terminationManager = new TerminationManager(this.o.getTimeBudgetDuration(), this.o.getTimeBudgetTimeUnit(), performerJBSE, performerEvosuite);
		
		//starts everything
		performerJBSE.start();
		performerEvosuite.start();
		terminationManager.start();
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