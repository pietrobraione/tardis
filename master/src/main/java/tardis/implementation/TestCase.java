package tardis.implementation;

import java.nio.file.Path;

import tardis.Options;

/**
 * A test case, i.e., a test method in a given test class.
 * It is immutable.
 * 
 * @author Pietro Braione
 */
public class TestCase {
    /**
     * The name of the class of the test method.
     */
    private final String className;
    
    /**
     * The descriptor of the parameters of the 
     * test method.
     */
    private final String methodDescriptor;
    
    /**
     * The name of the test method.
     */
    private final String methodName;
    
    /**
     * The {@link Path} of the source file of the 
     * test class.
     */
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
        this.sourcePath = sourceDir.resolve(this.className + ".java");
    }

    /**
     * Returns the test class name.
     * 
     * @return {@link String}, the name of the class of the test method.
     */
    public String getClassName() {
        return this.className;
    }

    /**
     * Returns the test method descriptor.
     * 
     * @return a {@link String}, the descriptor of the 
     *         parameters of the test method.
     */
    public String getMethodDescriptor() {
        return this.methodDescriptor;
    }

    /**
     * Returns the test method name.
     * 
     * @return a {@link String}, the name of the test method.
     */
    public String getMethodName() {
        return this.methodName;
    }

    /**
     * Returns the source file of 
     * the test class.
     * 
     * @return The {@link Path} of the 
     *         source file of the test class.
     */
    public Path getSourcePath() {
        return this.sourcePath;
    }
}
