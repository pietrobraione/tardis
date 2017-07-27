package concurrent;

import java.util.concurrent.TimeUnit;

import exec.Options;

public class TerminationManager {
	private final long duration;
	private final TimeUnit timeUnit;
	private final Performer<?,?>[] performers;
	
	public TerminationManager(Options o, Performer<?,?>...performers) {
		this.duration = o.getTimeBudgetDuration();
		this.timeUnit = o.getTimeBudgetTimeUnit();
		this.performers = performers.clone();
	}
	
	public void start() {
		final Thread chrono = new Thread(() ->  {
			try {
				this.timeUnit.sleep(this.duration);
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
