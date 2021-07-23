package tardis.implementation.evosuite;

import java.nio.file.Path;

/**
 * Exception thrown whenever the compilation of a test class failed.
 * 
 * @author Pietro Braione
 */
final class CompilationFailedTestException extends Exception {
    final Path file;
    
    /**
     * The serial version UID of the {@link CompilationFailedTestException} objects.
     */
    private static final long serialVersionUID = 5671727480233276853L;

    /**
     * Constructor.
     * 
     * @param file the {@link Path} of the file for the test class.
     */
    public CompilationFailedTestException(Path file) {
        this.file = file;
    }    
}
