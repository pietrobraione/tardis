package concurrent;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public final class TerminationManager {
	private final long duration;
	private final TimeUnit timeUnit;
	private final Performer<?,?>[] performers;
	private final Thread timeoutDetector;
	private final Thread terminationDetector;
	private volatile boolean timedOut;
	private Runnable actionOnStop = null;
		
	public TerminationManager(long duration, TimeUnit timeUnit, Performer<?,?>...performers) {
		this.duration = duration;
		this.timeUnit = timeUnit;
		this.performers = performers.clone();
		this.timedOut = false;
		this.timeoutDetector = new Thread(() -> {
			try {
				this.timeUnit.sleep(this.duration);
				this.timedOut = true;
			} catch (InterruptedException e) {
				//just terminates
			}
		});
		this.terminationDetector = new Thread(() -> {
			try {
				while (true) {
					TimeUnit.SECONDS.sleep(1);
					
					//exits upon timeout
					if (this.timedOut) {
						break;
					}
					
					//exits upon termination
					//double check
					final boolean allIdleUnsafe = allIdle();
					if (allIdleUnsafe) {
						//synchronizes and repeats the check
						pauseAll();
						final boolean allIdleSafe = allIdle();
						resumeAll();
						if (allIdleSafe) {
							this.timeoutDetector.interrupt();
							break;
						}
					}
				}
			} catch (InterruptedException e) {
				//this should never happen
				e.printStackTrace(); //TODO handle
			}
			
			//quits
			stopAll();
			if (this.actionOnStop != null) {
				this.actionOnStop.run();
			}
		});
	}
	
	private void pauseAll() {
		Arrays.stream(this.performers).forEach(Performer::pause);
	}
	
	private void resumeAll() {
		Arrays.stream(this.performers).forEach(Performer::resume);
	}
	
	private void stopAll() {
		Arrays.stream(this.performers).forEach(Performer::stop);
	}
	
	private boolean allIdle() {
		return Arrays.stream(this.performers).map(Performer::isIdle).reduce(Boolean.TRUE, (a, b) -> a && b);
	}
	
	public void start() {
		this.timeoutDetector.start();
		this.terminationDetector.start();
	}
	
	public void setOnStop(Runnable actionOnStop) {
		this.actionOnStop = actionOnStop;
	}
}
