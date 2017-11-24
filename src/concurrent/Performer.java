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
					if (!this.paused) {
						//it's a quit
						break;
					} //otherwise goes on to waitIfPaused()
				}
			}
			this.threadPool.shutdown();
		});
		this.lockPause = new ReentrantLock();
		this.conditionNotPaused = this.lockPause.newCondition();
		this.conditionPaused = this.lockPause.newCondition();
		this.paused = false;
		this.seed = null;
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
	 * Stops the performer. Should be invoked after {@link #start()}
	 * and never invoked when the performer is {@link #pause() pause}d.
	 */
	protected final void stop() {
		this.mainThread.interrupt();
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
		signalResumed();
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
	
	private ArrayList<I> items;
	
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

	private void signalResumed() {
		final ReentrantLock lock = this.lockPause;
		lock.lock();
		try {
			this.conditionNotPaused.signalAll();
		} finally {
			lock.unlock();
		}
	}
}
