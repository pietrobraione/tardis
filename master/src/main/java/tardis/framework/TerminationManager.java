package tardis.framework;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import tardis.Options;

/**
 * Component that detects if a set of {@link Performer}s is at a fixpoint, 
 * or if a timeout is expired, and in the case stops the {@link Performer}s.
 *  
 * @author Pietro Braione
 *
 */
public final class TerminationManager {
    /**
     * The maximum duration of the operativity of the {@link #performers}, 
     * i.e., the timeout.
     */
    private final long timeoutDuration;
    
    /**
     * The {@link TimeUnit} for {@link #timeoutDuration}.
     */
    private final TimeUnit timeoutTimeUnit;
    
    /**
     * The {@link Performer}s monitored by this {@link TerminationManager}.
     */
    private final Performer<?,?>[] performers;
    
    /**
     * The {@link Thread} that waits for {@link #timeoutDuration} and then stops
     * the {@link #performers} by interrupting {@link #detectorTermination}.
     */
    private final Thread detectorTimeout;
    
    /**
     * The {@link Thread} that periodically polls the {@link #performers}
     * to determine whether they are at the fixpoint, and finally stops them.
     */
    private final Thread detectorTermination;
    
    /**
     * Set to {@code true} by {@link #detectorTimeout} upon timeout.
     */
    private volatile boolean timedOut;

    /**
     * Constructor.
     * 
     * @param o the {@link Options}. 
     * @param performers a varargs of {@link Performer}s. The constructed {@link TerminationManager}
     *        will monitor them.
     * @throws NullPointerException if {@code timeUnit == null || performers == null}.
     */
    public TerminationManager(Options o, Performer<?, ?>... performers) {
        if (o == null || performers == null) {
            throw new NullPointerException("Invalid null parameter in termination manager constructor.");
        }
        this.timeoutDuration = o.getGlobalTimeBudgetDuration();
        this.timeoutTimeUnit = o.getGlobalTimeBudgetUnit();
        this.performers = performers.clone();
        this.timedOut = false;
        this.detectorTimeout = new Thread(() -> {
            try {
                this.timeoutTimeUnit.sleep(this.timeoutDuration);
                this.timedOut = true;
            } catch (InterruptedException e) {
                //this should never happen, but 
                //in the case we behave as it were
                //a timeout, just for safety
                this.timedOut = true;
            }
        }, "TerminationManager-detectorTimeout");
        this.detectorTermination = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    //this should never happen,
                    //in the case falling through 
                    //is ok
                }

                //exits upon timeout
                if (this.timedOut) {
                    break;
                }

                //exits upon termination
                //double check
                final boolean allIdleUnsafe = allIdle();
                if (allIdleUnsafe) {
                    //synchronizes and repeats the check
                    pauseAll();
                    final boolean allIdleSafe = allIdle();
                    resumeAll();
                    if (allIdleSafe) {
                        this.detectorTimeout.interrupt();
                        break;
                    }
                }
            }

            //quits
            stopAll();
        }, "TerminationManager-detectorTermination");
    }

    /**
     * Pauses all the performers.
     */
    private void pauseAll() {
        Arrays.stream(this.performers).forEach(Performer::pause);
    }

    /**
     * Resumes all the performers.
     */
    private void resumeAll() {
        Arrays.stream(this.performers).forEach(Performer::resume);
    }

    /**
     * Stops all the performers.
     */
    private void stopAll() {
        Arrays.stream(this.performers).forEach(Performer::stop);
    }

    /**
     * Checks whether all the performers are idle.
     * 
     * @return {@code true} iff {@code p.}{@link Performer#isIdle() isIdle}{@code () == true} for
     *         all {@code p in }{@link #performers}.
     */
    private boolean allIdle() {
        return Arrays.stream(this.performers).map(Performer::isIdle).reduce(Boolean.TRUE, (a, b) -> a && b);
    }

    /**
     * Starts this {@link TerminationManager}.
     */
    public void start() {
        this.detectorTimeout.start();
        this.detectorTermination.start();
    }

    /**
     * Makes the invoking thread wait until
     * all the {@link Performer}s passed upon 
     * construction terminate.
     * 
     * @throws InterruptedException if any thread
     *         has interrupted the invoking thread.
     */
    public void waitTermination() throws InterruptedException {
        this.detectorTermination.join();
    }
}
