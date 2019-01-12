package tardis.framework;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PausableFixedThreadPoolExecutor extends ThreadPoolExecutor {
	private final AtomicInteger activeThreads = new AtomicInteger(0);
	private final ReentrantLock lockPause = new ReentrantLock();
	private final Condition conditionNotPaused = this.lockPause.newCondition();
	private volatile boolean paused = false;
	
	//constructor
	
	public PausableFixedThreadPoolExecutor(int nThreads) {
		//stolen from Executors.newFixedThreadPool
		super(nThreads, nThreads, 
				0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
	}
	
	@Override
	public void execute(Runnable r) {
		this.activeThreads.incrementAndGet();
		super.execute(r);
	}
	
	@Override
	protected void beforeExecute(Thread t, Runnable r) {
		final ReentrantLock lock = this.lockPause;
		lock.lock();
		try {
			while (this.paused) {
				this.conditionNotPaused.await();
			}
		} catch (InterruptedException e) {
			//this should never happen
			e.printStackTrace(); //TODO handle
		} finally {
			lock.unlock();
		}
		super.beforeExecute(t, r); //pleonastic
	}
	
	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		this.activeThreads.decrementAndGet();
	}

	/**
	 * Pauses this thread pool.
	 */
	final void pause() {
		this.paused = true;
	}

	/**
	 * Resumes this thread pool from a {@link #pause()}.
	 */
	final void resume() {
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
	 * Checks if this thread pool is idle.
	 * 
	 * @return {@code true} iff this thread pool 
	 *         is idle, i.e., iff the thread pool 
	 *         is not handling any job and no pending 
	 *         jobs are in the input queue.
	 */
	final boolean isIdle() {
		return this.activeThreads.get() == 0;
	}
}
