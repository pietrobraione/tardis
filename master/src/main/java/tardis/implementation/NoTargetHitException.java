package tardis.implementation;

/**
 * Exception thrown when a test case does not hit 
 * its target method.
 * 
 * @author Pietro Braione
 *
 */
final class NoTargetHitException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = -7060402958023071977L;
    
    public NoTargetHitException() {
        //nothing to do
    }
}
