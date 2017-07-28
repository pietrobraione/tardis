package concurrent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class Performer<I,O> {
	private final LinkedBlockingQueue<I> in;
	private final LinkedBlockingQueue<O> out;
	private final PausableFixedThreadPoolExecutor threadPool;
	private final Thread mainThread;
	private final Lock lock = new ReentrantLock();
	private final Condition notPaused = this.lock.newCondition();
	private volatile boolean paused;

	public Performer(LinkedBlockingQueue<I> in, LinkedBlockingQueue<O> out, int numOfThreads) {
		this.in = in;
		this.out = out;
		this.threadPool = new PausableFixedThreadPoolExecutor(numOfThreads);
		this.mainThread = new Thread(() -> {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					this.lock.lock();
					try {
						while (this.paused) {
							this.notPaused.await();
						}
					} finally {
						this.lock.unlock();
					}
					final I item = getInputQueue().take();
					final Runnable job = makeJob(item);
					this.threadPool.execute(job);
				}
			} catch (InterruptedException e) {
				//triggered by stop(), just continue to shutdown
			}
			this.threadPool.shutdown();
		});
		this.paused = false;
	}

	protected abstract Runnable makeJob(I item);

	protected final LinkedBlockingQueue<I> getInputQueue() {
		return this.in;
	}

	protected final LinkedBlockingQueue<O> getOutputQueue() {
		return this.out;
	}
	
	public final void start() {
		this.mainThread.start();
	}
	
	protected final void stop() {
		this.mainThread.interrupt();
	}
	
	final void pause() {
		this.paused = true;
		this.threadPool.pause();
	}
	
	final void resume() {
		this.paused = false;
		this.threadPool.resume();
		this.lock.lock();
		try {
			this.notPaused.signalAll();
		} finally {
			this.lock.unlock();
		}
	}
	
	final boolean isIdle() {
		return this.in.isEmpty() && this.threadPool.isIdle();
	}
}
