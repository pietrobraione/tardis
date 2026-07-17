package tardis.implementation.evosuite;

import static tardis.implementation.common.Util.stringifyPostFrontierPathCondition;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tardis.Options;
import tardis.implementation.data.JBSEResultInputOutputBuffer;
import tardis.implementation.jbse.JBSEResult;

/**
 * A {@link Runnable} that listens for the output produced by 
 * an instance of EvoSuite, and when this produces a test
 * schedules JBSE for its analysis.
 * 
 * @author Pietro Braione
 */
final class TestDetector implements Runnable {
    private static final Logger LOGGER = LogManager.getFormatterLogger(PerformerEvosuite.class);
    
	private final PerformerEvosuite performerEvosuite;
	private final Options o;
    private final int testCountInitial;
    private final List<JBSEResult> items;
    private final BufferedReader evosuiteBufferedReader;
    private final Path evosuiteLogFilePath;
    private final BufferedWriter evosuiteLogFileWriter;
    private final JBSEResultInputOutputBuffer in;

    /**
     * Constructor.
     * 
     * @param performerEvosuite the invoking {@link PerformerEvosuite}.
     * @param o the {@link Options}.
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
    public TestDetector(PerformerEvosuite performerEvosuite, Options o, int testCountInitial, List<JBSEResult> items, InputStream evosuiteInputStream, Path evosuiteLogFilePath, JBSEResultInputOutputBuffer in) throws IOException {
    	this.performerEvosuite = performerEvosuite;
    	this.o = o;
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
            final Pattern patternEmittedTest = Pattern.compile("^.*\\* EMITTED TEST CASE .*EvoSuiteWrapper_(\\d+).*$");
            
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
                        this.performerEvosuite.checkTestCompileAndScheduleJBSE(testCount, item);
                    } catch (NoTestFileException e) {
                        LOGGER.error("Failed to generate the test case %s for post-frontier path condition %s:%s: the generated test class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
                        //continue
                    } catch (NoTestFileScaffoldingException e) {
                        LOGGER.error("Failed to generate the test case %s for post-frontier path condition %s:%s: the generated scaffolding class file does not seem to exist (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
                        //continue
                    } catch (NoTestMethodException e) {
                        LOGGER.warn("Failed to generate the test case %s for post-frontier path condition %s:%s: the generated files does not contain a test method (perhaps EvoSuite must be blamed)", e.file.toAbsolutePath().toString(), e.entryPoint, e.pathCondition);
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
                LOGGER.info("Failed to generate a test case for post-frontier path condition %s:%s, log file: %s, wrapper: EvoSuiteWrapper_%d", item.getTargetMethodSignature(), stringifyPostFrontierPathCondition(item), this.evosuiteLogFilePath.toString(), testCount);
                
                //learns for update of indices
                if (this.o.getUseIndexInfeasibility() && item.getPostFrontierState() != null) { //NB: item.getFinalState() == null for seed items when target is method
                	this.in.learnPathConditionForIndexInfeasibility(item.getTargetMethodSignature(), item.getPathConditionGenerated(), false);
                }

                //TODO possibly lazier updates of index
                if (this.o.getUseIndexInfeasibility() && item.getPostFrontierState() != null) {
                	this.in.updateIndexInfeasibilityAndReclassify();
                }
            }
            ++testCount;
        }
    }
}

