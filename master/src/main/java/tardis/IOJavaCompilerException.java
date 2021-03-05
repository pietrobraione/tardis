package tardis;

import java.io.IOException;

/**
 * Exception thrown whenever the Java compiler process cannot be created, 
 * or communication with it fails (exception raised only when probing the
 * Java compiler version).
 * 
 * @author Pietro Braione
 */
final class IOJavaCompilerException extends Exception {
    final IOException e;
    
    /**
     * The serial version UID of the {@link IOJavaCompilerException} objects.
     */
    private static final long serialVersionUID = -1231072711799427806L;

    /**
     * Constructor.
     * 
     * @param e the {@link IOException} raised during creation of the Java
     *        compiler process or communication with it.
     */
    public IOJavaCompilerException(IOException e) {
        this.e = e;
    }
}
