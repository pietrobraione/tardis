package exec;

public class TestCase {
	private final String className;
	private final String methodDescriptor;
	private final String methodName;
	
	/**
	 * Constructor. Builds a {@link TestCase} for the 
	 * initial test case.
	 * 
	 * @param o a {@link Options} object.
	 */
	public TestCase(Options o) {
		this.className = o.getInitialTestCase().get(0);
		this.methodDescriptor = o.getInitialTestCase().get(1);
		this.methodName = o.getInitialTestCase().get(2);
	}
	
	/**
	 * Constructor. Builds a {@link TestCase} from the specification 
	 * of a test method.
	 * 
	 * @param className a {@link String}, the name of the class of the test method.
	 * @param methodDescriptor a {@link String}, the descriptor of the parameters of the 
	 *        test method.
	 * @param methodName a {@link String}, the name of the test method.
	 */
	public TestCase(String className, String methodDescriptor, String methodName) {
		this.className = className;
		this.methodDescriptor = methodDescriptor;
		this.methodName = methodName;
	}

	/**
	 * Copy constructor.
	 * 
	 * @param otherTc a {@link TestCase}.
	 */
	public TestCase(TestCase otherTc) {
		this.className = otherTc.getClassName();
		this.methodDescriptor = otherTc.getMethodDescriptor();
		this.methodName = otherTc.getMethodName();
	}
	
	public String getClassName(){
		return this.className;
	}
	
	public String getMethodDescriptor(){
		return this.methodDescriptor;
	}
	
	public String getMethodName(){
		return this.methodName;
	}
}
