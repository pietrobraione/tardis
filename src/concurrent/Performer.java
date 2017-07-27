package concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class Performer<I,O>{
	private final LinkedBlockingQueue<I> in;
	private final LinkedBlockingQueue<O> out;
	private final ExecutorService threadPool;
	private final Thread mainThread;

	public Performer(LinkedBlockingQueue<I> in, LinkedBlockingQueue<O> out, int numOfThreads) {
		this.in = in;
		this.out = out;
		this.threadPool = Executors.newFixedThreadPool(numOfThreads);
		this.mainThread = new Thread(() -> {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					final I item = getInputQueue().take();
					final Runnable job = makeJob(item);
					this.threadPool.execute(job);
				}
			} catch (InterruptedException e) {
				//interrupted while taking from input queue: just continue to shutdown
			}
			this.threadPool.shutdown();
		});
	}

	protected abstract Runnable makeJob(I item);

	protected LinkedBlockingQueue<I> getInputQueue() {
		return this.in;
	}

	protected LinkedBlockingQueue<O> getOutputQueue() {
		return this.out;
	}
	
	public void start() {
		this.mainThread.start();
	}
	
	protected void stop() {
		this.mainThread.interrupt();
	}
}
