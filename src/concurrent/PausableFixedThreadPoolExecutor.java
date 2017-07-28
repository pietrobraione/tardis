package concurrent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PausableFixedThreadPoolExecutor extends ThreadPoolExecutor {
	private final AtomicInteger activeThreads = new AtomicInteger(0);
	private final Lock lock = new ReentrantLock();
	private final Condition notPaused = this.lock.newCondition();
	private volatile boolean paused = false;
	
	//constructor
	
	public PausableFixedThreadPoolExecutor(int nThreads) {
		//stolen from Executors.newFixedThreadPool
		super(nThreads, nThreads, 
				0L, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>());
	}
	
	@Override
	protected void beforeExecute(Thread t, Runnable r) {
		this.activeThreads.incrementAndGet();
		super.beforeExecute(t, r);
	}

	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		this.lock.lock();
		try {
			while (this.paused) {
				this.notPaused.await();
			}
			this.activeThreads.decrementAndGet();
			super.afterExecute(r, t);
		} catch (InterruptedException e) {
			//this should never happen
			e.printStackTrace(); //TODO handle
		} finally {
			this.lock.unlock();
		}
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
		this.lock.lock();
		try {
			this.notPaused.signalAll();
		} finally {
			this.lock.unlock();
		}
	}

	/**
	 * Checks if this thread pool is idle.
	 * This method can be invoked only when this 
	 * thread pool is {@link #pause()}d.
	 * 
	 * @return {@code true} iff the input queue of
	 *         this thread pool is empty and no thread
	 *         in the thread pool is active.
	 */
	final boolean isIdle() {
		return getQueue().isEmpty() && this.activeThreads.get() == 0;
	}
}
