package exec;

import java.util.concurrent.LinkedBlockingQueue;

import concurrent.TerminationManager;

public class Main {
	private Options o;
	
	public Main(Options o) {
		this.o = o;
	}
	
	public void start() {
		//creates the communication queues between the performers
		final LinkedBlockingQueue<JBSEResult> pathConditionBuffer = new LinkedBlockingQueue<>();
		final LinkedBlockingQueue<EvosuiteResult> testCaseBuffer = new LinkedBlockingQueue<>();
		
		//seeds testCaseBuffer with the initial test case
		final TestCase tc = new TestCase(this.o);
		final EvosuiteResult item = new EvosuiteResult(tc, 0);
		testCaseBuffer.add(item);
		
		//creates and wires the components of the architecture
		final PerformerJBSE performerJBSE = new PerformerJBSE(this.o, testCaseBuffer, pathConditionBuffer);
		final PerformerEvosuite performerEvosuite = new PerformerEvosuite(this.o, pathConditionBuffer, testCaseBuffer);
		final TerminationManager terminationManager = new TerminationManager(this.o, performerJBSE, performerEvosuite);
		
		//starts everything
		performerJBSE.start();
		performerEvosuite.start();
		terminationManager.start();
	}
}