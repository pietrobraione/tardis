package tardis.implementation;

/**
 * Exception thrown whenever the Java 8 JDK environment is missing.
 * 
 * @author Pietro Braione
 */
public final class NoJava8ToolsJarException extends Exception {
    /**
     * The serial version UID of the {@link NoJava8ToolsJarException} objects.
     */
    private static final long serialVersionUID = -878393100697088603L;

    /**
     * Constructor.
     */
    public NoJava8ToolsJarException() {
        //nothing to do
    }
}
