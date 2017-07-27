package concurrent;

import java.util.concurrent.TimeUnit;

public class TimeoutManager {
	private final long duration;
	private final TimeUnit timeUnit;
	private final Performer<?,?>[] performers;
	
	public TimeoutManager(long duration, TimeUnit timeUnit, Performer<?,?>...performers) {
		this.duration = duration;
		this.timeUnit = timeUnit;
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
