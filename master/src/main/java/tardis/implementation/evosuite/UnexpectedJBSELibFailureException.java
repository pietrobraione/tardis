package tardis.implementation.evosuite;

/**
 * Exception thrown whenever another exception occurred when using the JBSE library.
 * 
 * @author Pietro Braione
 */
final class UnexpectedJBSELibFailureException extends Exception {
	final Exception e;
    
    /**
     * The serial version UID of the {@link UnexpectedJBSELibFailureException} objects.
     */
    private static final long serialVersionUID = 2195406660824949938L;

    /**
     * Constructor.
     * 
     * @param e the {@link Exception} raised when the use of the JBSE library 
     *        was attempted.
     */
    public UnexpectedJBSELibFailureException(Exception e) {
        this.e = e;
    }
}
