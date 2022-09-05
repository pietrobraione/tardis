package tardis.framework;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Performer} based on a set of servers with a given capacity to which
 * the work is dispatched.
 * 
 * @author Pietro Braione
 *
 * @param <I> The type of the items that are read from the {@link InputBuffer}.
 * @param <O> The type of the items that are put in the {@link OutputBuffer}.
 */
public abstract class PerformerMultiServer<I,O> extends Performer<I,O> {
    /**
     * The pausable thread pool that this {@link PerformerMultiServer} 
     * uses to communicate with the servers.
     */
    private final PausableFixedThreadPoolExecutor threadPool;
    
    /** The total number of server workers. */
    private final int totalWorkers;
    
    /**
     * Constructor.
     * 
     * @param name a meaningful name for the performer that will be used for debugging.
     * @param in The {@link InputBuffer} from which this {@link PerformerMultiServer} will read the input items. 
     * @param out The {@link OutputBuffer} where this {@link PerformerMultiServer} will put the output items.
     * @param numServers The number of servers that this {@link PerformerMultiServer} encapsulates.
     * @param serverCapacity The capacity of each server expressed as the maximum number of simultaneous jobs
     *        a server is able to handle.
     * @param numInputs An {@code int}, the maximum number of input items that are passed as a batch
     *        to {@link #makeJob(List) makeJob}.
     * @param throttleFactor The throttle factor; it must be between 0 and 1. When 0, a batch is 
     *        taken from {@code in} and passed to {@link #makeJob(List) makeJob} whenever
     *        there are sufficient items. When 1, it is also required that at least one worker 
     *        is available. Intermediate values yield different degrees of throttling. 
     * @param timeoutDuration The maximum duration of the time this {@link PerformerMultiServer} will wait for 
     *        the arrival of an input item.  
     * @param timeoutTimeUnit The {@link TimeUnit} for {@code timeoutDuration}. 
     * @throws NullPointerException if {@code in == null || out == null || timeoutUnit == null}.
     * @throws IllegalArgumentException if {@code numOfServers <= 0 || serverCapacity <= 0 || numInputs <= 0 || timeoutDuration < 0}.
     */
    public PerformerMultiServer(String name, InputBuffer<I> in, OutputBuffer<O> out, int numServers, int serverCapacity, int numInputs, float throttleFactor, long timeoutDuration, TimeUnit timeoutTimeUnit) {
    	super(name, in, out, numServers * serverCapacity, numInputs, throttleFactor, timeoutDuration, timeoutTimeUnit);
        if (numServers <= 0 || serverCapacity <= 0) {
            throw new IllegalArgumentException("Invalid negative or zero parameter in PerformerMultiServer constructor.");
        }
    	this.totalWorkers = numServers * serverCapacity;
        this.threadPool = new PausableFixedThreadPoolExecutor(name, numServers);
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
    	return this.threadPool.isIdle() && availableWorkers() == this.totalWorkers;
    }
    
    @Override
    protected final void execute(Runnable job) {
    	this.threadPool.execute(job);
    }
}
