package concurrent;

import java.util.concurrent.TimeUnit;

import exec.Options;

public class TerminationManager {
	private long duration;
	private TimeUnit timeUnit;
	private Performer<?,?>[] performers;
	
	public TerminationManager(Options o, Performer<?,?>...performers) {
		this.duration = o.getTimeBudgetDuration();
		this.timeUnit = o.getTimeBudgetTimeUnit();
		this.performers = performers;
	}
	
	public void execute() {
		final Thread chrono = new Thread(() ->  {
			try {
				Thread.sleep(this.timeUnit.toMillis(this.duration));
			} catch (InterruptedException e) {
				//should never happen
				e.printStackTrace();
			}
			for (Performer<?,?> p : this.performers) {
				p.stop();
			}
		});
		chrono.start();
	}
}
