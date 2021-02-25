package tardis.implementation;

import java.nio.file.Path;

/**
 * Exception thrown whenever the compilation of a scaffolding class failed.
 * 
 * @author Pietro Braione
 */
class CompilationFailedTestScaffoldingException extends Exception {
    final Path file;
    
    /**
     * The serial version UID of the {@link CompilationFailedTestScaffoldingException} objects.
     */
    private static final long serialVersionUID = 4868542293386516015L;

    /**
     * Constructor.
     * 
     * @param file the {@link Path} of the file for the scaffolding class.
     */
    public CompilationFailedTestScaffoldingException(Path file) {
        this.file = file;
    }    
}
