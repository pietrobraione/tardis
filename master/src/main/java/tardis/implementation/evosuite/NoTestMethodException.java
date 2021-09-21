package tardis.implementation.evosuite;

import java.nio.file.Path;

/**
 * Exception thrown whenever the test method is missing in a test file.
 * 
 * @author Pietro Braione
 */
final class NoTestMethodException extends Exception {
    final Path file;
	final Object entryPoint;
    final String pathCondition;
    
    /**
     * The serial version UID of the {@link NoTestMethodException} objects.
     */
    private static final long serialVersionUID = -1343457517104144500L;

    /**
     * Constructor.
     * 
     * @param file the {@link Path} of the file for the test class.
     * @param entryPoint the target method signature to which {@code pathCondition} 
     *        refers as a {@link String}. 
     * @param pathCondition the path condition as a {@link String}.
     */
    public NoTestMethodException(Path file, String entryPoint, String pathCondition) {
        this.file = file;
        this.entryPoint = entryPoint;
        this.pathCondition = pathCondition;
    }
    
    /**
     * Constructor.
     * 
     * @param testFile the {@link Path} of the file for the test class.
     */
    public NoTestMethodException(Path testFile) {
        this(testFile, null, null);
    }
}
