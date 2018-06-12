package concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class Performer<I,O> {
	private final InputBuffer<I> in;
	private final OutputBuffer<O> out;
	private final PausableFixedThreadPoolExecutor threadPool;
	private final int numInputs;
	private final long timeoutDuration;
	private final TimeUnit timeoutUnit;
	private final Thread mainThread;
	private final ReentrantLock lockPause;
	private final Condition conditionNotPaused;
	private final Condition conditionPaused;
	private volatile boolean paused;
	private ArrayList<I> seed;	
	private ArrayList<I> items;

	public Performer(InputBuffer<I> in, OutputBuffer<O> out, int numOfThreads, int numInputs, long timeoutDuration, TimeUnit timeoutUnit) {
		this.in = in;
		this.out = out;
		this.threadPool = new PausableFixedThreadPoolExecutor(numOfThreads);
		this.numInputs = numInputs;
		this.timeoutDuration = timeoutDuration;
		this.timeoutUnit = timeoutUnit;
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
			this.threadPool.shutdown();
		});
		this.lockPause = new ReentrantLock();
		this.conditionNotPaused = this.lockPause.newCondition();
		this.conditionPaused = this.lockPause.newCondition();
		this.paused = false;
		this.seed = null;
		this.items = null;
	}
	
	protected abstract Runnable makeJob(List<I> items);

	protected final OutputBuffer<O> getOutputBuffer() {
		return this.out;
	}
	
	/**
	 * Seeds the performer with a set of initial items,
	 * that are executed immediately as the performer 
	 * is started. Should be invoked before {@link #start()}.
	 * 
	 * @param seed an {@link ArrayList}{@code <I>} containing
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
	 * Stops the performer. Should be invoked after {@link #start()}.
	 */
	final void stop() {
		this.paused = false;
		this.mainThread.interrupt();
		this.threadPool.shutdownNow();
	}
	
	/**
	 * Pauses the performer so it can reliably be queried whether 
	 * it {@link #isIdle()}.
	 */
	final void pause() {
		this.paused = true;
		final ReentrantLock lock = this.lockPause;
		lock.lock();
		try {
			this.mainThread.interrupt(); //if mainThread is waiting, interrupt it so it will go in pause state and signal conditionPaused; guarded by lockPause so the next await() will not lose the signal from mainThread
			this.conditionPaused.await(); //waits until mainThread pauses 
		} catch (InterruptedException e) {
			//this should never happen
			e.printStackTrace(); //TODO handle
		} finally {
			lock.unlock();
		}
		this.threadPool.pause();
	}

	/**
	 * Resumes the performer after a {@link #pause()}.
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
		return this.in.isEmpty() && this.items.isEmpty() && this.threadPool.isIdle();
	}
	
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
		final I item = this.in.poll(this.timeoutDuration, this.timeoutUnit);
		if (item != null) {
			this.items.add(item);
		}
		if ((item == null && this.items.size() > 0) || this.items.size() == this.numInputs) {
			final Runnable job = makeJob(this.items);
			this.threadPool.execute(job);
			this.items = null;
		}
	}
}
