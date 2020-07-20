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
     * The {@link Path} where the package hierarchy
     * of the test class and scaffolding class starts.
     */
    private final Path basePath;
    
    /**
     * The {@link Path} of the source file of the 
     * test class.
     */
    private final Path sourcePath;
    
    /**
     * The {@link Path} of the source file of the 
     * scaffolding class, or {@code null} if the
     * test class has no scaffolding.
     */
    private final Path scaffoldingPath;
    
    /**
     * Constructor. Builds a {@link TestCase} for the 
     * initial test case.
     * 
     * @param o a {@link Options} object.
     * @param hasScaffolding {@code true} iff this test case has scaffolding.
     */
    public TestCase(Options o, boolean hasScaffolding) {
        this.className = o.getInitialTestCase().get(0);
        this.methodDescriptor = o.getInitialTestCase().get(1);
        this.methodName = o.getInitialTestCase().get(2);
        this.basePath = o.getInitialTestCasePath();
        this.sourcePath = this.basePath.resolve(this.className + ".java");
        this.scaffoldingPath = (hasScaffolding ? this.basePath.resolve(this.className + "_scaffolding.java") : null);
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
     * @param hasScaffolding {@code true} iff this test case has scaffolding.
     */
    public TestCase(String className, String methodDescriptor, String methodName, Path sourceDir, boolean hasScaffolding) {
        this.className = className;
        this.methodDescriptor = methodDescriptor;
        this.methodName = methodName;
        this.basePath = sourceDir;
        this.sourcePath = this.basePath.resolve(this.className + ".java");
        this.scaffoldingPath = (hasScaffolding ? this.basePath.resolve(this.className + "_scaffolding.java") : null);
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
     * Returns the directory where
     * the package hierarchy of the
     * test class starts.
     * 
     * @return the {@link Path} of the
     *         directory where the package
     *         hierarchy of the source file
     *         of the test (and scaffolding)
     *         class starts. It is a prefix 
     *         of {@link #getSourcePath()}
     *         and {@link #getScaffoldingPath()}
     *         (the latter when it is not {@code null}).
     */
    public Path getBasePath() {
        return this.basePath;
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

    /**
     * Returns the source file of 
     * the scaffolding class.
     * 
     * @return The {@link Path} of the 
     *         source file of the scaffolding class.
     */
    public Path getScaffoldingPath() {
        return this.scaffoldingPath;
    }
}
