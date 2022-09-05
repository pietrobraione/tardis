package tardis.framework;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Performer} based on a {@link PausableFixedThreadPoolExecutor}.
 * 
 * @author Pietro Braione
 *
 * @param <I> The type of the items that are read from the {@link InputBuffer}.
 * @param <O> The type of the items that are put in the {@link OutputBuffer}.
 */
public abstract class PerformerPausableFixedThreadPoolExecutor<I,O> extends Performer<I,O> {
    /**
     * The pausable thread pool of all the threads
     * that this {@link PerformerPausableFixedThreadPoolExecutor} encapsulates.
     */
    private final PausableFixedThreadPoolExecutor threadPool;
    
    /**
     * Constructor.
     * 
     * @param name a meaningful name for the performer that will be used for debugging.
     * @param in The {@link InputBuffer} from which this {@link PerformerPausableFixedThreadPoolExecutor} will read the input items. 
     * @param out The {@link OutputBuffer} where this {@link PerformerPausableFixedThreadPoolExecutor} will put the output items.
     * @param numOfThreads The number of concurrent threads that this {@link PerformerPausableFixedThreadPoolExecutor} encapsulates.
     * @param numInputs An {@code int}, the maximum number of input items that are passed as a batch
     *        to {@link #makeJob(List) makeJob}.
     * @param throttleFactor The throttle factor; it must be between 0 and 1. When 0, a batch is 
     *        taken from {@code in} and passed to {@link #makeJob(List) makeJob} whenever
     *        there are sufficient items. When 1, it is also required that at least one worker 
     *        is available. Intermediate values yield different degrees of throttling. 
     * @param timeoutDuration The maximum duration of the time this {@link PerformerPausableFixedThreadPoolExecutor} will wait for 
     *        the arrival of an input item.  
     * @param timeoutTimeUnit The {@link TimeUnit} for {@code timeoutDuration}. 
     * @throws NullPointerException if {@code in == null || out == null || timeoutUnit == null}.
     * @throws IllegalArgumentException if {@code numOfThreads <= 0 || numInputs <= 0 || timeoutDuration < 0}.
     */
    public PerformerPausableFixedThreadPoolExecutor(String name, InputBuffer<I> in, OutputBuffer<O> out, int numOfThreads, int numInputs, float throttleFactor, long timeoutDuration, TimeUnit timeoutTimeUnit) {
    	super(name, in, out, numOfThreads, numInputs, throttleFactor, timeoutDuration, timeoutTimeUnit);
        this.threadPool = new PausableFixedThreadPoolExecutor(name, numOfThreads);
    }
    
    @Override
    protected final void onPause() {
        this.threadPool.pause();
    }
    
    @Override
    protected final void onResume() {
        this.threadPool.resume();
    }
    
    @Override
    protected final void onShutdown() {
    	this.threadPool.shutdownNow();
    }

    @Override
    protected final boolean areWorkersIdle() {
    	return this.threadPool.isIdle();
    }
    
    @Override
    protected final int availableWorkers() {
    	return this.threadPool.getCorePoolSize() - this.threadPool.getActiveCount();
    }
    
    @Override
    protected final void execute(Runnable job) {
    	this.threadPool.execute(job);
    }
}
