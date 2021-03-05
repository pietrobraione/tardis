package tardis.implementation;

import java.nio.file.Path;

/**
 * Exception thrown whenever the scaffolding file for a test is missing.
 * 
 * @author Pietro Braione
 */
final class NoTestFileScaffoldingException extends Exception {
    final Path file;
    final String pathCondition;
   
    /**
     * The serial version UID of the {@link NoTestFileScaffoldingException} objects.
     */
    private static final long serialVersionUID = -5947978922304952215L;

    /**
     * Constructor.
     * 
     * @param file the {@link Path} of the file for the scaffolding class.
     * @param pathCondition the path condition as a {@link String}.
     */
    public NoTestFileScaffoldingException(Path file, String pathCondition) {
        this.file = file;
        this.pathCondition = pathCondition;
    }
    
    /**
     * Constructor.
     * 
     * @param scaffFile the {@link Path} of the file for the scaffolding class.
     */
    public NoTestFileScaffoldingException(Path scaffFile) {
        this(scaffFile, null);
    }
}
