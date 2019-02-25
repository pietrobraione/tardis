package tardis.implementation;

public class EvosuiteResult {
	private final String targetMethodClassName;
	private final String targetMethodDescriptor;
	private final String targetMethodName;
	private final TestCase tc;
	private final int startDepth;
	
	public EvosuiteResult(String targetMethodClassName, String targetMethodDescriptor, String targetMethodName, TestCase tc, int startDepth) {
		this.targetMethodClassName = targetMethodClassName;
		this.targetMethodDescriptor = targetMethodDescriptor;
		this.targetMethodName = targetMethodName;
		this.tc = new TestCase(tc);
		this.startDepth = startDepth;
	}
	
	public EvosuiteResult(JBSEResult jr, TestCase tc, int startDepth) {
		this.targetMethodClassName = jr.getTargetClassName();
		this.targetMethodDescriptor = jr.getTargetMethodDescriptor();
		this.targetMethodName = jr.getTargetMethodName();
		this.tc = new TestCase(tc);
		this.startDepth = startDepth;
	}
	
	public String getTargetMethodClassName() {
		return this.targetMethodClassName;
	}
	
	public String getTargetMethodDescriptor() {
		return this.targetMethodDescriptor;
	}
	
	public String getTargetMethodName() {
		return this.targetMethodName;
	}
	
	public TestCase getTestCase() {
		return this.tc;
	}
	
	public int getStartDepth() {
		return this.startDepth;
	}
}
