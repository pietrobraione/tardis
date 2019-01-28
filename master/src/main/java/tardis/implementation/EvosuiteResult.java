package tardis.implementation;

public class EvosuiteResult {
	private final String targetClassName;
	private final String targetMethodDescriptor;
	private final String targetMethodName;
	private final TestCase tc;
	private final int startDepth;
	
	public EvosuiteResult(String targetClassName, String targetMethodDescriptor, String targetMethodName, TestCase tc, int startDepth) {
		this.targetClassName = targetClassName;
		this.targetMethodDescriptor = targetMethodDescriptor;
		this.targetMethodName = targetMethodName;
		this.tc = new TestCase(tc);
		this.startDepth = startDepth;
	}
	
	public EvosuiteResult(JBSEResult jr, TestCase tc, int startDepth) {
		this.targetClassName = jr.getTargetClassName();
		this.targetMethodDescriptor = jr.getTargetMethodDescriptor();
		this.targetMethodName = jr.getTargetMethodName();
		this.tc = new TestCase(tc);
		this.startDepth = startDepth;
	}
	
	public String getTargetClassName() {
		return this.targetClassName;
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
