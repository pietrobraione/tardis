package tardis.implementation;

import java.util.List;

/**
 * A work item produced by the Evosuite performer and 
 * consumed by the JBSE performer. It encapsulates a 
 * {@link TestCase}.
 * 
 * @author Pietro Braione
 */
public class EvosuiteResult {
    /**
     * The name of the class
     * where the target method is defined.
     */
    private final String targetMethodClassName;
    
    /**
     * The descriptor of the target method.
     */
    private final String targetMethodDescriptor;
    
    /**
     * The name of the target method.
     */
    private final String targetMethodName;
    
    /**
     * A {@link TestCase}.
     */
    private final TestCase testCase;
    
    /**
     * The depth starting from which the 
     * {@link #testCase} must be analyzed 
     * by the JBSE performer.
     */
    private final int startDepth;

    /**
     * Constructor.
     * 
     * @param targetMethodClassName A {@link String}, the name of the class
     *        where the target method is defined.
     * @param targetMethodDescriptor A {@link String}, the descriptor of the
     *        target method.
     * @param targetMethodName A {@link String}, the name of the target method.
     * @param testCase A {@link TestCase}. Its execution should hit {@code targetMethod}.
     * @param startDepth A positive {@code int}, indicating the depth starting 
     *        from which the {@code testCase} must be analyzed by the JBSE performer.
     */
    public EvosuiteResult(String targetMethodClassName, String targetMethodDescriptor, String targetMethodName, TestCase testCase, int startDepth) {
        this.targetMethodClassName = targetMethodClassName;
        this.targetMethodDescriptor = targetMethodDescriptor;
        this.targetMethodName = targetMethodName;
        this.testCase = testCase;
        this.startDepth = startDepth;
    }

    /**
     * Constructor. Equivalent to {@link #EvosuiteResult(String, String, String, TestCase, int) EvosuiteResult}{@code (targetMethod.get(0), targetMethod.get(1), targetMethod.get(2), testCase, startDepth)}.
     * 
     * @param targetMethod A {@link List}{@code <}{@link String}{@code >} that
     *        must have (at least) length 3.
     * @param testCase A {@link TestCase}. Its execution should hit {@code targetMethod}.
     * @param startDepth A positive {@code int}, indicating the depth starting 
     *        from which the {@code testCase} must be analyzed by the JBSE performer.
     */
    public EvosuiteResult(List<String> targetMethod, TestCase testCase, int startDepth) {
        this(targetMethod.get(0), targetMethod.get(1), targetMethod.get(2), testCase, startDepth);
    }

    /**
     * Returns the name of the class
     * where the target method is defined.
     * 
     * @return A {@link String}.
     */
    public String getTargetMethodClassName() {
        return this.targetMethodClassName;
    }

    /**
     * Returns the descriptor of the target method.
     * 
     * @return A {@link String}.
     */
    public String getTargetMethodDescriptor() {
        return this.targetMethodDescriptor;
    }

    /**
     * Returns the name of the target method.
     * 
     * @return A {@link String}.
     */
    public String getTargetMethodName() {
        return this.targetMethodName;
    }

    /**
     * Returns the {@link TestCase}.
     * 
     * @return A {@link TestCase}.
     */
    public TestCase getTestCase() {
        return this.testCase;
    }

    /**
     * Returns the depth starting 
     * from which the {@link #getTestCase() test case} 
     * must be analyzed by the JBSE performer.
     * 
     * @return An {@code int}.
     */
    public int getStartDepth() {
        return this.startDepth;
    }
}
