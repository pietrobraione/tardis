package exec;

import java.nio.file.Path;

public class TestCase {
	private final String className;
	private final String methodDescriptor;
	private final String methodName;
	private final Path sourcePath;
	
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
		this.sourcePath = o.getInitialTestCasePath().resolve(this.className + ".java");
	}
	
	/**
	 * Constructor. Builds a {@link TestCase} from the specification 
	 * of a test method.
	 * 
	 * @param className a {@link String}, the name of the class of the test method.
	 * @param methodDescriptor a {@link String}, the descriptor of the parameters of the 
	 *        test method.
	 * @param methodName a {@link String}, the name of the test method.
	 * @param sourceDir the {@link Path} of the directory where the source file of the 
	 *        test case is found.
	 */
	public TestCase(String className, String methodDescriptor, String methodName, Path sourceDir) {
		this.className = className;
		this.methodDescriptor = methodDescriptor;
		this.methodName = methodName;
		this.sourcePath = sourceDir.resolve(className + ".java");
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
		this.sourcePath = otherTc.getSourcePath();
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
	
	public Path getSourcePath() {
		return this.sourcePath;
	}
}
