package tardis;

/**
 * Exception thrown whenever the Java 8 JDK environment is missing.
 * 
 * @author Pietro Braione
 */
final class NoJava8JVMException extends Exception {
    /**
     * The serial version UID of the {@link NoJava8JVMException} objects.
     */
    private static final long serialVersionUID = 5489930052711934360L;

    /**
     * Constructor.
     */
    public NoJava8JVMException() {
        //nothing to do
    }
}
