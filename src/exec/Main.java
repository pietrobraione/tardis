package exec;

import java.util.concurrent.LinkedBlockingQueue;

import concurrent.TerminationManager;

public class Main {
	private Options o;
	
	public Main(Options o) {
		this.o = o;
	}
	
	public void execute() {
		final LinkedBlockingQueue<JBSEResult> pathConditionBuffer = new LinkedBlockingQueue<>();
		final LinkedBlockingQueue<EvosuiteResult> testCaseBuffer = new LinkedBlockingQueue<>();
		final TestCase tc = new TestCase(this.o);
		final EvosuiteResult item = new EvosuiteResult(tc, 0);
		testCaseBuffer.add(item);
		final PathExplorer performerJBSE = new PathExplorer(this.o, testCaseBuffer, pathConditionBuffer);
		final PathConditionHandler performerEvosuite = new PathConditionHandler(this.o, pathConditionBuffer, testCaseBuffer);
		final TerminationManager terminationManager = new TerminationManager(this.o, performerJBSE, performerEvosuite);
		
		//starts
		performerJBSE.execute();
		performerEvosuite.execute();
		terminationManager.execute();
	}
}