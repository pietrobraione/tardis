package tardis.implementation.evosuite;

/**
 * Exception thrown whenever a class file cannot be accessed.
 * 
 * @author Pietro Braione
 */
final class ClassFileAccessException extends Exception {
    final Throwable e;
    final String className;
    
    /**
     * The serial version UID of the {@link ClassFileAccessException} objects.
     */
    private static final long serialVersionUID = 5349774223661988085L;

    /**
     * Constructor.
     * 
     * @param e the {@link Throwable} raised when access was attempted.
     * @param file a {@link String}, the name of the class that could not be accessed.
     */
    public ClassFileAccessException(Throwable e, String className) {
        this.e = e;
        this.className = className;
        
    }
}
