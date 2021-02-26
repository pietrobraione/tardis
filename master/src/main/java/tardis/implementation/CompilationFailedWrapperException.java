package tardis.implementation;

import java.nio.file.Path;

/**
 * Exception thrown whenever the compilation of a wrapper class failed.
 * 
 * @author Pietro Braione
 */
class CompilationFailedWrapperException extends Exception {
    final Path file;
    
    /**
     * The serial version UID of the {@link CompilationFailedWrapperException} objects.
     */
    private static final long serialVersionUID = 3863847468945763982L;

    /**
     * Constructor.
     * 
     * @param file the {@link Path} of the file for the test class.
     */
    public CompilationFailedWrapperException(Path file) {
        this.file = file;
    }    
}
