package tardis;

/**
 * Exception thrown whenever the Java compiler throws an {@link Exception}.
 * 
 * @author Pietro Braione
 *
 */
final class JavaCompilerException extends Exception {
    /**
     * The serial version UID of the {@link JavaCompilerException} objects.
     */
    private static final long serialVersionUID = 8187862492506082191L;

    /**
     * Constructor with cause.
     * 
     * @param cause The {@link Throwable} thrown by the Java compiler.
     */
    public JavaCompilerException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor with (message, cause).
     * 
     * @param message A {@link String}.
     * @param cause The {@link Throwable} thrown by the Java compiler.
     */
    public JavaCompilerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor with (message, cause, enableSuppression, writableStackTrace).
     * 
     * @param message A {@link String}.
     * @param cause The {@link Throwable} thrown by the Java compiler.
     * @param enableSuppression A {@code boolean}; When {@code true} suppression
     *        is enabled.
     * @param writableStackTrace A {@code boolean}; When {@code true} stack
     *        trace is writeable.
     */
    public JavaCompilerException(String message, Throwable cause, boolean enableSuppression,
    boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
