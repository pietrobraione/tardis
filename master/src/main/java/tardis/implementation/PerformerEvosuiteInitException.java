package tardis.implementation;

/**
 * Exception thrown whenever the initialization of the Evosuite 
 * performer fails.
 * 
 * @author Pietro Braione
 */
public final class PerformerEvosuiteInitException extends Exception {
    /**
     * The serial version UID of the {@link PerformerEvosuiteInitException} objects.
     */
    private static final long serialVersionUID = -9140435797636087230L;

    /**
     * Constructor.
     */
    public PerformerEvosuiteInitException() {
    }

    /**
     * Constructor with message.
     * 
     * @param message A {@link String}.
     */
    public PerformerEvosuiteInitException(String message) {
        super(message);
    }

    /**
     * Constructor with cause.
     * 
     * @param cause The {@link Throwable} thrown during the initialization
     *        of the Evosuite performer.
     */
    public PerformerEvosuiteInitException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor with (message, cause).
     * 
     * @param message A {@link String}.
     * @param cause The {@link Throwable} thrown during the initialization
     *        of the Evosuite performer.
     */
    public PerformerEvosuiteInitException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructor with (message, cause, enableSuppression, writableStackTrace).
     * 
     * @param message A {@link String}.
     * @param cause The {@link Throwable} thrown during the initialization
     *        of the Evosuite performer.
     * @param enableSuppression A {@code boolean}; When {@code true} suppression
     *        is enabled.
     * @param writableStackTrace A {@code boolean}; When {@code true} stack
     *        trace is writeable.
     */
    public PerformerEvosuiteInitException(String message, Throwable cause, boolean enableSuppression,
    boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
