package concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class Performer<I,O>{
	
	private LinkedBlockingQueue<I> in;
	private LinkedBlockingQueue<O> out;
	private int numOfThreads;
	private ExecutorService threadPool;
	
	public Performer(LinkedBlockingQueue<I> in, LinkedBlockingQueue<O> out, int numOfThreads){
		this.in = in;
		this.out = out;
		this.numOfThreads = numOfThreads;
		this.threadPool = Executors.newFixedThreadPool(this.numOfThreads);
	}
	
	protected abstract Runnable makeJob(I item);
	
	protected LinkedBlockingQueue<I> getInputQueue(){
		return this.in;
	}
	
	protected LinkedBlockingQueue<O> getOutputQueue(){
		return this.out;
	}
	
	public void execute(){
		try {
			Runnable job = this.makeJob(getInputQueue().take());
			this.threadPool.execute(job);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}
