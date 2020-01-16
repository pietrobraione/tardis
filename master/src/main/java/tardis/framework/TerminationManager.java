package tardis.framework;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Component that detects if a set of {@link Performer}s is at a fixpoint, 
 * or if a timeout is expired, and in the case stops the {@link Performer}s.
 *  
 * @author Pietro Braione
 *
 */
public final class TerminationManager {
    private final long duration;
    private final TimeUnit timeUnit;
    private final Performer<?,?>[] performers;
    private final Thread timeoutDetector;
    private final Thread terminationDetector;
    private volatile boolean timedOut;

    /**
     * Constructor.
     * 
     * @param duration the maximum duration of the operativity of the {@code performers}, 
     *        i.e., the timeout.
     * @param timeUnit the {@link TimeUnit} for {@code duration}.
     * @param performers a varargs of {@link Performer}s. The constructed {@link TerminationManager}
     *        will monitor the {@code performers}.
     * @throws NullPointerException if {@code timeUnit == null || performers == null}.
     */
    public TerminationManager(long duration, TimeUnit timeUnit, Performer<?,?>...performers) {
        if (timeUnit == null || performers == null) {
            throw new NullPointerException();
        }
        this.duration = duration;
        this.timeUnit = timeUnit;
        this.performers = performers.clone();
        this.timedOut = false;
        this.timeoutDetector = new Thread(() -> {
            try {
                this.timeUnit.sleep(this.duration);
                this.timedOut = true;
            } catch (InterruptedException e) {
                //just terminates
            }
        });
        this.terminationDetector = new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    //this should never happen,
                    //in the case we fall through
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
                        this.timeoutDetector.interrupt();
                        break;
                    }
                }
            }

            //quits
            stopAll();
        });
    }

    private void pauseAll() {
        Arrays.stream(this.performers).forEach(Performer::pause);
    }

    private void resumeAll() {
        Arrays.stream(this.performers).forEach(Performer::resume);
    }

    private void stopAll() {
        Arrays.stream(this.performers).forEach(Performer::stop);
    }

    private boolean allIdle() {
        return Arrays.stream(this.performers).map(Performer::isIdle).reduce(Boolean.TRUE, (a, b) -> a && b);
    }

    /**
     * Starts this {@link TerminationManager}.
     */
    public void start() {
        this.timeoutDetector.start();
        this.terminationDetector.start();
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
        this.terminationDetector.join();
    }
}
