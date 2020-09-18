package tardis.framework;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A performer encapsulates a set of concurrent threads. Each thread repeatedly reads 
 * input items from a common {@link InputBuffer}, elaborates each of them, possibly 
 * producing (one or more) output items. The output items are put then in a common 
 * {@link OutputBuffer}.
 * 
 * @author Pietro Braione
 *
 * @param <I> The type of the items that are read from the {@link InputBuffer}.
 * @param <O> The type of the items that are put in the {@link OutputBuffer}.
 */
public abstract class Performer<I,O> {
    /**
     * The {@link InputBuffer} from which this 
     * {@link Performer} will read the input items.
     */
    private final InputBuffer<I> in;
    
    /**
     * The {@link OutputBuffer} where this {@link Performer} 
     * will put the output items.
     */
    private final OutputBuffer<O> out;
    
    /**
     * The pausable thread pool of all the threads
     * that this {@link Performer} encapsulates.
     */
    private final PausableFixedThreadPoolExecutor threadPool;
    
    /**
     * The maximum number of input items that are passed as a batch
     * to {@link #makeJob(List) makeJob}.
     */
    private final int numInputs;
    
    /**
     * The throttle factor; it must be between 0 and 1.
     * When 0, a batch is taken from {@link #in} and
     * passed to {@link #makeJob(List) makeJob} whenever
     * there are sufficient items. When 1, it is also 
     * required that at least one worker in {@link #threadPool}
     * is available. Intermediate values yield different 
     * degrees of throttling. 
     */
    private final float throttleFactor;
    
    /**
     * The maximum duration of the time this {@link Performer} will wait for 
     * the arrival of an input item. 
     */
    private final long timeoutDuration;
    
    /**
     * The {@link TimeUnit} for {@code timeoutDuration}.
     */
    private final TimeUnit timeoutTimeUnit;
    
    /**
     * The main {@link Thread}, that takes batches of input items from {@link #in}
     * and dispatches them to {@link #threadPool}.
     */
    private final Thread mainThread;
    
    /**
     * A {@link ReentrantLock} that is used to synchronize
     * {@link #mainThread} upon pause. 
     */
    private final ReentrantLock lockPause;
    
    /**
     * A {@link Condition} associated to {@link #lockPause} 
     * that is notified whenever this {@link Performer} is 
     * resumed from a pause.
     */
    private final Condition conditionNotPaused;
    
    /**
     * A {@link Condition} associated to {@link #lockPause} 
     * that is notified whenever this {@link Performer} is 
     * paused.
     */
    private final Condition conditionPaused;
    
    /**
     * Set to {@code true} whenever this {@link Performer} is paused.
     */
    private volatile boolean paused;
    
    /**
     * Set to {@code true} whenever this {@link Performer} is stopped.
     */
    private volatile boolean stopped;
    
    /**
     * A seed, i.e., a batch of input items set by 
     * {@link #seed(List) seed} to start the {@link Performer}.
     */
    private ArrayList<I> seed;
    
    /**
     * The batch of input items that is progressively built
     * by {@link InputBuffer#poll(long, TimeUnit) poll}ing {@link #in}. 
     */
    private ArrayList<I> items;

    /**
     * Constructor.
     * 
     * @param in The {@link InputBuffer} from which this {@link Performer} will read the input items. 
     * @param out The {@link OutputBuffer} where this {@link Performer} will put the output items.
     * @param numOfThreads The number of concurrent threads that this {@link Performer} encapsulates.
     * @param numInputs An {@code int}, the maximum number of input items that are passed as a batch
     *        to {@link #makeJob(List) makeJob}.
     * @param throttleFactor The throttle factor; it must be between 0 and 1. When 0, a batch is 
     *        taken from {@code in} and passed to {@link #makeJob(List) makeJob} whenever
     *        there are sufficient items. When 1, it is also required that at least one worker 
     *        is available. Intermediate values yield different degrees of throttling. 
     * @param timeoutDuration The maximum duration of the time this {@link Performer} will wait for 
     *        the arrival of an input item.  
     * @param timeoutTimeUnit The {@link TimeUnit} for {@code timeoutDuration}. 
     * @throws NullPointerException if {@code in == null || out == null || timeoutUnit == null}.
     * @throws IllegalArgumentException if {@code numOfThreads <= 0 || numInputs <= 0 || timeoutDuration < 0}.
     */
    public Performer(InputBuffer<I> in, OutputBuffer<O> out, int numOfThreads, int numInputs, float throttleFactor, long timeoutDuration, TimeUnit timeoutTimeUnit) {
        if (in == null || out == null || timeoutTimeUnit == null) {
            throw new NullPointerException("Invalid null parameter in performer constructor.");
        }
        if (numOfThreads <= 0 || numInputs <= 0 || timeoutDuration < 0) {
            throw new IllegalArgumentException("Invalid negative parameter in performer constructor.");
        }
        this.in = in;
        this.out = out;
        this.threadPool = new PausableFixedThreadPoolExecutor(numOfThreads);
        this.numInputs = numInputs;
        this.throttleFactor = throttleFactor;
        this.timeoutDuration = timeoutDuration;
        this.timeoutTimeUnit = timeoutTimeUnit;
        this.mainThread = new Thread(() -> {
            submitSeedIfPresent();
            while (true) {
                try {
                    waitIfPaused();
                    waitInputAndSubmitJob();
                } catch (InterruptedException e) {
                    if (this.paused) {
                        //interrupted by pause(): goes on to waitIfPaused()
                        continue; //pleonastic
                    } else {
                        //interrupted by stop(): exit from the loop 
                        break;
                    } 
                }
            }
            this.threadPool.shutdownNow();
        });
        this.lockPause = new ReentrantLock();
        this.conditionNotPaused = this.lockPause.newCondition();
        this.conditionPaused = this.lockPause.newCondition();
        this.paused = false;
        this.stopped = false;
        this.seed = null;
        this.items = null;
    }

    /**
     * Makes a {@link Runnable} job to be executed by a thread encapsulated by 
     * this performer.
     * 
     * @param items a {@link List}{@code <I>}, a batch of input items whose minimum
     *        size is 1 and whose maximum size is the {@code numInputs} parameter
     *        passed upon construction (with the possible exception of the seed items, 
     *        that are not split according to {@code numInputs} but are always passed 
     *        as a unique batch).
     * @return a {@link Runnable} that elaborates {@code items}, possibly putting
     *         some output items in the output buffer.
     */
    protected abstract Runnable makeJob(List<I> items);

    /**
     * Gets the {@link OutputBuffer} of this {@link Performer}. Meant
     * to be used in the subclasses to implement {@link #makeJob(List)}.
     * 
     * @return an {@link OutputBuffer}.
     */
    protected final OutputBuffer<O> getOutputBuffer() {
        return this.out;
    }

    /**
     * Seeds the {@link Performer} with a set of initial items,
     * that are executed immediately as the performer 
     * is started. Should be invoked before {@link #start()}.
     * 
     * @param seed a {@link List}{@code <I>} containing
     *        the items that seed the performer.
     */
    public final void seed(List<I> seed) {
        this.seed = new ArrayList<>(seed);
    }

    /**
     * Starts the performer.
     */
    public final void start() {
        this.mainThread.start();
    }

    /**
     * Stops the performer. Shall be invoked after {@link #start()}.
     */
    final void stop() {
        this.paused = false;
        this.stopped = true;
        this.mainThread.interrupt();
        this.threadPool.shutdownNow(); //pleonastic
    }

    /**
     * Pauses the performer so it can reliably be queried whether 
     * it {@link #isIdle()}.
     */
    final void pause() {
        this.paused = true;
        
        //if this performer is stopped, there is
        //nothing else to do
        if (this.stopped) {
            return;
        }
        
        //if mainThread is waiting, interrupts it so it will go 
        //in pause state and signal conditionPaused; guarded by 
        //lockPause so the next await() will not lose the signal 
        //from mainThread
        final ReentrantLock lock = this.lockPause;
        lock.lock();
        try {
            this.mainThread.interrupt(); 
            this.conditionPaused.await(); //waits until mainThread pauses 
        } catch (InterruptedException e) {
            //this should never happen
            stop();
            throw new AssertionError(e);
        } finally {
            lock.unlock();
        }
        
        //pauses the thread pool
        this.threadPool.pause();
    }

    /**
     * Resumes this performer from a {@link #pause()}.
     */
    final void resume() {
        this.threadPool.resume();
        this.paused = false;
        final ReentrantLock lock = this.lockPause;
        lock.lock();
        try {
            this.conditionNotPaused.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Checks whether this performer is idle. 
     * This method gives a reliable answer only when the
     * performed is {@link #pause() pause}d.
     * 
     * @return {@code true} iff the performer is idle, i.e., 
     * iff all the threads are waiting and the input queue is
     * empty.
     */
    final boolean isIdle() {
        return this.stopped || (this.in.isEmpty() && (this.items == null || this.items.isEmpty()) && this.threadPool.isIdle());
    }

    /**
     * To be invoked by the main thread. Submits the seed to 
     * the thread pool, if present.
     */
    private void submitSeedIfPresent() {
        if (this.seed == null) {
            return;
        }
        final Runnable job = makeJob(this.seed);
        this.threadPool.execute(job);
    }

    /**
     * To be invoked by the main thread. Detects whether
     * this performer is {@link #pause() paused}, and in 
     * the positive case waits until it is {@link #resume() resumed}.
     *
     * @throws InterruptedException if the main thread is
     *         interrupted while waiting to be resumed.
     */
    private void waitIfPaused() throws InterruptedException {
        final ReentrantLock lock = this.lockPause;
        lock.lock();
        try {
            this.conditionPaused.signalAll();
            while (this.paused) {
                this.conditionNotPaused.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * To be invoked by the main thread. Waits for an input
     * item in the input queue up to a timeout, accumulates 
     * the read input items, and when they are enough (or
     * upon timeout) creates a job for processing them and 
     * submits the job to the thread pool.
     * 
     * @throws InterruptedException if the main thread is
     *         interrupted while waiting for an input.
     */
    private void waitInputAndSubmitJob() throws InterruptedException {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        
        //throttles
        if (this.items.size() == 0 && this.threadPool.getActiveCount() == this.threadPool.getCorePoolSize() && 
            this.throttleFactor > 0 && this.threadPool.getTaskCount() >= 1 / this.throttleFactor) {
            return;
        }
            
        //polls
        final I item = this.in.poll(this.timeoutDuration, this.timeoutTimeUnit);
        if (item != null) {
            this.items.add(item);
        }
        
        //submits job
        if ((item == null && this.items.size() > 0) || this.items.size() == this.numInputs) {
            final Runnable job = makeJob(this.items);
            this.threadPool.execute(job);
            this.items = null;
        }
    }
}
