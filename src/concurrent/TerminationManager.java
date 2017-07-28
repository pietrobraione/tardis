package concurrent;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class TerminationManager {
	private final long duration;
	private final TimeUnit timeUnit;
	private final Performer<?,?>[] performers;
	private final ShutdownManager shutdownManager;
	private final Thread terminationDetector;
	
	private class ShutdownManager extends Thread {
		private Thread toStop;
		
		public void setToStop(Thread toStop) {
			this.toStop = toStop;
		}
		
		@Override
		public void run() {
			try {
				TerminationManager.this.timeUnit.sleep(TerminationManager.this.duration);
				this.toStop.interrupt();
			} catch (InterruptedException e) {
				//possibly interrupted by termination detector, 
				//go on and stop all performers
			}
			for (Performer<?,?> p : TerminationManager.this.performers) {
				p.stop();
			}
		}
	}
	
	public TerminationManager(long duration, TimeUnit timeUnit, Performer<?,?>...performers) {
		this.duration = duration;
		this.timeUnit = timeUnit;
		this.performers = performers.clone();
		this.shutdownManager = new ShutdownManager();
		this.terminationDetector = new Thread(() -> {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					TimeUnit.SECONDS.sleep(1);
					for (Performer<?,?> p : this.performers) {
						p.pause();
					}
					final boolean allIdle = Arrays.stream(this.performers).map(Performer::isIdle).reduce(Boolean.TRUE, (a, b) -> a && b);
					for (Performer<?,?> p : this.performers) {
						p.resume();
					}
					if (allIdle) {
						this.shutdownManager.interrupt();
						return; //terminates
					}
				}
			} catch (InterruptedException e) {
				//just terminates
			}
		});
		this.shutdownManager.setToStop(this.terminationDetector);
	}
	
	public void start() {
		shutdownManager.start();

		terminationDetector.start();
	}
}
