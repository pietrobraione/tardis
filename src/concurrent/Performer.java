package concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public abstract class Performer<I,O> {
	private final InputBuffer<I> in;
	private final OutputBuffer<O> out;
	private final long timeout;
	private final TimeUnit unit;
	private final PausableFixedThreadPoolExecutor threadPool;
	private final int numInputs;
	private final Thread mainThread;
	private final ReentrantLock lockPause;
	private final Condition conditionNotPaused;
	private final Condition conditionPaused;
	private volatile boolean paused;

	public Performer(InputBuffer<I> in, OutputBuffer<O> out, long timeout, TimeUnit unit, int numOfThreads, int numInputs) {
		this.in = in;
		this.out = out;
		this.timeout = timeout;
		this.unit = unit;
		this.threadPool = new PausableFixedThreadPoolExecutor(numOfThreads);
		this.numInputs = numInputs;
		this.mainThread = new Thread(() -> {
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
	}
	
	protected abstract Runnable makeJob(List<I> item);

	protected final OutputBuffer<O> getOutputBuffer() {
		return this.out;
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
		final I item = this.in.poll(this.timeout, this.unit);
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
