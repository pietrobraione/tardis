package tardis.implementation.evosuite;

import java.nio.file.Path;

/**
 * Exception thrown whenever the file for a test is missing.
 * 
 * @author Pietro Braione
 */
final class NoTestFileException extends Exception {
    final Path file;
	final Object entryPoint;
    final String pathCondition;
    
    /**
     * The serial version UID of the {@link NoTestFileException} objects.
     */
    private static final long serialVersionUID = 2349136663366040652L;

    /**
     * Constructor.
     * 
     * @param file the {@link Path} of the file for the test class.
     * @param entryPoint the target method signature to which {@code pathCondition} 
     *        refers as a {@link String}. 
     * @param pathCondition the path condition as a {@link String}.
     */
    public NoTestFileException(Path file, String entryPoint, String pathCondition) {
        this.file = file;
        this.entryPoint = entryPoint;
        this.pathCondition = pathCondition;
        
    }
    
    /**
     * Constructor.
     * 
     * @param testFile the {@link Path} of the file for the test class.
     */
    public NoTestFileException(Path testFile) {
        this(testFile, null, null);
    }
}
