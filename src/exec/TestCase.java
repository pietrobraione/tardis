package exec;

public class TestCase {
	private final String className;
	private final String parameterSignature;
	private final String methodName;
	
	/**
	 * Constructor. Builds a {@link TestCase} for the 
	 * initial test case.
	 * 
	 * @param o a {@link Options} object.
	 */
	public TestCase(Options o) {
		this.className = o.getInitialTestCase().get(0);
		this.parameterSignature = o.getInitialTestCase().get(1);
		this.methodName = o.getInitialTestCase().get(2);
	}
	
	/**
	 * Constructor. Builds a {@link TestCase} from the specification 
	 * of a test method.
	 * 
	 * @param className a {@link String}, the name of the class of the test method.
	 * @param parameterSignature a {@link String}, the signature of the parameters of the 
	 *        test method.
	 * @param methodName a {@link String}, the name of the test method.
	 */
	public TestCase(String className, String parameterSignature, String methodName) {
		this.className = className;
		this.parameterSignature = parameterSignature;
		this.methodName = methodName;
	}

	/**
	 * Copy constructor.
	 * 
	 * @param otherTc a {@link TestCase}.
	 */
	public TestCase(TestCase otherTc) {
		this.className = otherTc.getClassName();
		this.parameterSignature = otherTc.getParameterSignature();
		this.methodName = otherTc.getMethodName();
	}
	
	public String getClassName(){
		return this.className;
	}
	
	public String getParameterSignature(){
		return this.parameterSignature;
	}
	
	public String getMethodName(){
		return this.methodName;
	}
}
