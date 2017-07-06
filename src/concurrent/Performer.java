package concurrent;

import java.util.concurrent.LinkedBlockingQueue;

public abstract class Performer {
	
	public LinkedBlockingQueue<JBSEResult> q1 = new LinkedBlockingQueue<>();
	public LinkedBlockingQueue<EvosuiteResult> q2 = new LinkedBlockingQueue<>();
	public int numOfThreads;
	
	public Performer(LinkedBlockingQueue<JBSEResult> q1, LinkedBlockingQueue<EvosuiteResult> q2, int numOfThreads){
		this.q1 = q1;
		this.q2 = q2;
		this.numOfThreads = numOfThreads;
	}
	
	public abstract void execute();
}
