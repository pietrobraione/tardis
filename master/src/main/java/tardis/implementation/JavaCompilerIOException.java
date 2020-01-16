package tardis.implementation;

public class JavaCompilerIOException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 8187862492506082191L;

    public JavaCompilerIOException() {
    }

    public JavaCompilerIOException(String message) {
        super(message);
    }

    public JavaCompilerIOException(Throwable cause) {
        super(cause);
    }

    public JavaCompilerIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public JavaCompilerIOException(String message, Throwable cause, boolean enableSuppression,
    boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
