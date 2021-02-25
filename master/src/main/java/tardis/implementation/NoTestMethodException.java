package tardis.implementation;

import java.nio.file.Path;

/**
 * Exception thrown whenever the test method is missing in a test file.
 * 
 * @author Pietro Braione
 */
class NoTestMethodException extends Exception {
    final Path file;
    final String pathCondition;
    
    /**
     * The serial version UID of the {@link NoTestMethodException} objects.
     */
    private static final long serialVersionUID = -1343457517104144500L;

    /**
     * Constructor.
     * 
     * @param file the {@link Path} of the file for the test class.
     * @param pathCondition the path condition as a {@link String}.
     */
    public NoTestMethodException(Path file, String pathCondition) {
        this.file = file;
        this.pathCondition = pathCondition;
        
    }
    
    /**
     * Constructor.
     * 
     * @param testFile the {@link Path} of the file for the test class.
     */
    public NoTestMethodException(Path testFile) {
        this(testFile, null);
    }
}
