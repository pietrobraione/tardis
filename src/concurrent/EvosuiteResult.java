package concurrent;

import exec.TestCase;

public class EvosuiteResult {
	private TestCase tc;
	private int startDepth;
	
	public EvosuiteResult(TestCase tc, int startDepth){
		this.tc = new TestCase(tc);
		this.startDepth = startDepth;
	}
	
	public void setTestCase(TestCase tc){
		this.tc = new TestCase(tc);
	}
	
	public TestCase getTestCase(){
		return this.tc;
	}
	
	public void setStartDepth(int startDepth){
		this.startDepth = startDepth;
	}
	
	public int getStartDepth(){
		return this.startDepth;
	}
}
