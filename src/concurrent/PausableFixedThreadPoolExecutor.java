package concurrent;

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
	public void execute(Runnable command) {
		this.activeThreads.incrementAndGet();
		super.execute(command);
	}
	
	@Override
	protected void afterExecute(Runnable r, Throwable t) {
		this.lockPause.lock();
		try {
			while (this.paused) {
				this.conditionNotPaused.await();
			}
			this.activeThreads.decrementAndGet();
			super.afterExecute(r, t);
		} catch (InterruptedException e) {
			//this should never happen
			e.printStackTrace(); //TODO handle
		} finally {
			this.lockPause.unlock();
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
		this.lockPause.lock();
		try {
			this.conditionNotPaused.signalAll();
		} finally {
			this.lockPause.unlock();
		}
	}
	
	/**
	 * Checks if this thread pool is idle
	 * (best effort).
	 * 
	 * @return {@code true} iff this thread pool 
	 *         is idle, i.e., iff the thread pool 
	 *         is not handling any job and no pending 
	 *         jobs are in the input queue. Note that, 
	 *         since observing the state of a thread 
	 *         pool without introducing races is almost (?)
	 *         impossible, this method <em>has</em> races.
	 *         To have a correct result with high probability
	 *         you should first {@link #pause()} the thread
	 *         pool, then possibly wait a bit,  
	 *         and only at this point invoke this method. 
	 *         
	 */
	final boolean isIdle() {
		//This method is imperfect in that it may be subject to races. This 
		//because the assumption that, if the queue is empty and the active
		//thread count is zero, then the thread pool is idle, is *wrong*. 
		//For instance it could be the case that the queue is empty, 
		//the count of the active threads is zero, and then a client thread calls
		//the execute method: before the execute method puts the job in the queue or 
		//delivers it to a thread the queue stays empty, the count stays to zero, 
		//but the thread pool is not idle. It could also be the case that all the 
		//threads are idle and there is exactly one job in the queue: when a thread  
		//in the pool extracts the job from the queue, before it invokes beforeExecute,
		//the queue is empty and the count is zero 
		//TODO fix if possible the races
		return getQueue().isEmpty() && this.activeThreads.get() == 0;
	}
}
