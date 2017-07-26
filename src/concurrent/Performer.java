package concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class Performer<I,O>{

	private LinkedBlockingQueue<I> in;
	private LinkedBlockingQueue<O> out;
	private int numOfThreads;
	private boolean active;
	private ExecutorService threadPool;

	public Performer(LinkedBlockingQueue<I> in, LinkedBlockingQueue<O> out, int numOfThreads) {
		this.in = in;
		this.out = out;
		this.numOfThreads = numOfThreads;
		this.active = true;
		this.threadPool = Executors.newFixedThreadPool(this.numOfThreads);
	}

	protected abstract Runnable makeJob(I item);

	protected LinkedBlockingQueue<I> getInputQueue() {
		return this.in;
	}

	protected LinkedBlockingQueue<O> getOutputQueue() {
		return this.out;
	}
	
	synchronized void stop() {
		this.active = false;
	}
	
	private synchronized boolean active() {
		return this.active;
	}

	public void execute() {
		final Thread t = new Thread(() -> {
			while (active()) {
				try {
					final I item = getInputQueue().poll(100, TimeUnit.MILLISECONDS);
					if (item == null) {
						//do nothing
					} else {
						final Runnable job = makeJob(item);
						this.threadPool.execute(job);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			this.threadPool.shutdown();
		});
		t.start();
	}
}
