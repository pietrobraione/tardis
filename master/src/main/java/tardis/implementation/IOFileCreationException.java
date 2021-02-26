package tardis.implementation;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Exception thrown whenever a file (typically a log file) cannot be created.
 * 
 * @author Pietro Braione
 */
class IOFileCreationException extends Exception {
    final IOException e;
    final Path file;
    
    /**
     * The serial version UID of the {@link IOFileCreationException} objects.
     */
    private static final long serialVersionUID = -1231072711799427806L;

    /**
     * Constructor.
     * 
     * @param e the {@link IOException} raised when creation was attempted.
     * @param file the {@link Path} of the file that could not be created.
     */
    public IOFileCreationException(IOException e, Path file) {
        this.e = e;
        this.file = file;
        
    }
}
