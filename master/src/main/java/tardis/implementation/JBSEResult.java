package tardis.implementation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jbse.mem.Clause;
import jbse.mem.State;

/**
 * A work item produced by the JBSE performer and 
 * consumed by the Evosuite performer. It represents a 
 * path to a frontier. It is immutable.
 * 
 * @author Pietro Braione
 */
public class JBSEResult {
    /**
     * The name of the target class, or {@code null}
     * if the target is a method.
     */
    private final String targetClassName;
    
    /**
     * The name of the class of the target method,
     * or {@code null} if the target is a class.
     */
    private final String targetMethodClassName;
    
    /**
     * The descriptor of the target method,
     * or {@code null} if the target is a class.
     */
    private final String targetMethodDescriptor;
    
    /**
     * The name of the target method, or {@code null} 
     * if the target is a class.
     */
    private final String targetMethodName;
    
    /**
     * The initial {@link State} of the path, or {@code null}
     * if this {@link JBSEResult} is a seed item.
     */
    private final State initialState;
    
    /**
     * The pre-frontier {@link State} of the path, or {@code null}
     * if this {@link JBSEResult} is a seed item.
     */
    private final State preState;
    
    /**
     * The final (post-frontier) {@link State} of the path, or 
     * {@code null} if this {@link JBSEResult} is a seed item.
     */
    private final State finalState;
    
    /**
     * Set to {@code true} iff the frontier is a 
     * jump bytecode ({@code false} if this 
     * {@link JBSEResult} is a seed item).
     */
    private final boolean atJump;
    
    /**
     * A {@link String} that identifies the target
     * branch, or {@code null} if 
     * {@link #atJump}{@code == false} or this 
     * {@link JBSEResult} is a seed item.
     * 
     */
    private final String targetBranch;
    
    /**
     * The string literals gathered during the
     * execution of the path to the frontier, or 
     * {@code null} if this {@link JBSEResult} is a seed item.
     */
    private final HashMap<Long, String> stringLiterals;
    
    /**
     * The (nonconstant) strings gathered during the
     * execution of the path to the frontier, or 
     * {@code null} if this {@link JBSEResult} is a seed item.
     */
    private final HashSet<Long> stringOthers;
    
    /**
     * The depth of the path to the frontier, or 
     * {@code -1} if this {@link JBSEResult} is a seed item.
     */
    private final int depth;
    
    /**
     * The novelty index: the minimum of the values relating to
     * how many times branches of finalState were hit by the tests.
     */
    private int noveltyIndex;
    
    /**
     * The improvability index: the size of notCoveredBranches list.
     */
    private int improvabilityIndex;
    
    /**
     * List of lists of clauses relating to not covered paths;
     * used for the improvability index calculation.
     */
    List<List<Clause>> notCoveredBranches;

    /**
     * Constructor for seed item (target method).
     * 
     * @param targetMethod A {@link List}{@code <}{@link String}{@code >}
     *        with length (at least) 3, the signature of the target method.
     *        The name of the class of the target method is {@code targetMethod.}{@link List#get(int) get}{@code (0)}, 
     *        the descriptor of the target method is {@code targetMethod.}{@link List#get(int) get}{@code (1)},
     *        the name of the target method is {@code targetMethod.}{@link List#get(int) get}{@code (2)}.
     */
    public JBSEResult(List<String> targetMethod) {
        this.targetClassName = null;
        this.targetMethodClassName = targetMethod.get(0);
        this.targetMethodDescriptor = targetMethod.get(1);
        this.targetMethodName = targetMethod.get(2);
        this.initialState = null;
        this.preState = null;
        this.finalState = null;
        this.atJump = false;
        this.targetBranch = null;
        this.stringLiterals = null;
        this.stringOthers = null;
        this.depth = -1;
        this.noveltyIndex = 0;
        this.improvabilityIndex = 0;
        this.notCoveredBranches = null;
    }

    /**
     * Constructor for seed item (target class).
     * 
     * @param targetClassName A {@link String}, the name of the target class.
     */
    public JBSEResult(String targetClassName) {
        this.targetClassName = targetClassName;
        this.targetMethodClassName = null;
        this.targetMethodDescriptor = null;
        this.targetMethodName = null;
        this.initialState = null;
        this.preState = null;
        this.finalState = null;
        this.atJump = false;
        this.targetBranch = null;
        this.stringLiterals = null;
        this.stringOthers = null;
        this.depth = -1;
        this.noveltyIndex = 0;
        this.improvabilityIndex = 0;
        this.notCoveredBranches = null;
    }

    /**
     * Constructor for non-seed item (target method only).
     * 
     * @param targetMethodClassName A {@link String}, 
     *        the name of the class of the target method.
     * @param targetMethodDescriptor A {@link String}, the 
     *        descriptor of the target method.
     * @param targetMethodName A {@link String}, the name 
     *        of the target method.
     * @param initialState The initial {@link State} of the path.
     * @param preState The pre-frontier {@link State} of the path.
     * @param finalState The final (post-frontier) {@link State} 
     *        of the path.
     * @param atJump A {@code boolean}, set to {@code true} iff 
     *        the frontier is a jump bytecode.
     * @param targetBranch A {@link String} that identifies the target
     *        branch. If {@code atJump == false} it is irrelevant.
     * @param stringLiterals a {@link Map}{@code <}{@link Long}{@code , }{@link String}{@code >}, 
     *        the string literals gathered during the execution of 
     *        the path to the frontier.
     * @param stringOthers a {@link Set}{@code <}{@link Long}{@code >}, 
     *        containing all the heap positions of the (nonconstant) 
     *        {@link String} objects gathered during the execution of 
     *        the path to the frontier. 
     * @param depth A positive {@code int}, the depth of the path 
     *        to the frontier.
     */
    public JBSEResult(String targetMethodClassName, String targetMethodDescriptor, String targetMethodName, State initialState, State preState, State finalState, boolean atJump, String targetBranch, Map<Long, String> stringLiterals, Set<Long> stringOthers, int depth, int noveltyIndex, int improvabilityIndex, List<List<Clause>> notCoveredBranches) {
        this.targetClassName = null;
        this.targetMethodClassName = targetMethodClassName;
        this.targetMethodDescriptor = targetMethodDescriptor;
        this.targetMethodName = targetMethodName;
        this.initialState = initialState.clone();
        this.preState = preState.clone();
        this.finalState = finalState.clone();
        this.atJump = atJump;
        this.targetBranch = (atJump ? targetBranch : null);
        this.stringLiterals = new HashMap<>(stringLiterals); //safety copy
        this.stringOthers = new HashSet<>(stringOthers);     //safety copy
        this.depth = depth;
        this.noveltyIndex = noveltyIndex;
        this.improvabilityIndex = improvabilityIndex;
        this.notCoveredBranches = notCoveredBranches;
    }
    
    /**
     * Checks whether this is a seed item.
     * 
     * @return {@code true} iff this is a seed item.
     */
    public boolean isSeed() {
        return this.initialState == null;
    }
    
    /**
     * Checks whether this item has a target
     * method.
     * 
     * @return {@code true} iff this item has
     *         a target method (otherwise, it 
     *         is a seed item and it has a 
     *         target class).
     */
    public boolean hasTargetMethod() {
        return this.targetClassName == null; 
    }

    /**
     * Gets the name of the target class. 
     * 
     * @return A {@link String}, or {@code null}
     *         if {@link #hasTargetMethod() hasTargetMethod}{@code () == true}.
     */
    public String getTargetClassName() {
        return this.targetClassName;
    }

    /**
     * Gets the name of the class of the target 
     * method.
     * 
     * @return A {@link String}, or {@code null}
     *         if {@link #hasTargetMethod() hasTargetMethod}{@code () == false}.
     */
    public String getTargetMethodClassName() {
        return this.targetMethodClassName;
    }

    /**
     * Gets the descriptor of the target 
     * method.
     * 
     * @return A {@link String}, or {@code null}
     *         if {@link #hasTargetMethod() hasTargetMethod}{@code () == false}.
     */
    public String getTargetMethodDescriptor() {
        return this.targetMethodDescriptor;
    }

    /**
     * Gets the name of the target 
     * method.
     * 
     * @return A {@link String}, or {@code null}
     *         if {@link #hasTargetMethod() hasTargetMethod}{@code () == false}.
     */
    public String getTargetMethodName() {
        return this.targetMethodName;
    }

    /**
     * Gets the initial {@link State} of the path.
     * 
     * @return A {@link State}, or {@code null}
     *         if {@link #isSeed() isSeed}{@code () == true}.
     */
    public State getInitialState() {
        return this.initialState;
    }

    /**
     * Gets the pre-frontier {@link State} of the path.
     * 
     * @return A {@link State}, or {@code null}
     *         if {@link #isSeed() isSeed}{@code () == true}.
     */
    public State getPreState() {
        return this.preState;
    }

    /**
     * Gets the final (post-frontier) {@link State} of the path.
     * 
     * @return A {@link State}, or {@code null}
     *         if {@link #isSeed() isSeed}{@code () == true}.
     */
    public State getFinalState() {
        return this.finalState;
    }

    /**
     * Gets whether the frontier the frontier is a 
     * jump bytecode.
     * 
     * @return {@code true} iff the frontier is a jump
     *         bytecode ({@code false}
     *         if {@link #isSeed() isSeed}{@code () == true}).
     */
    public boolean getAtJump() {
        return this.atJump;
    }

    /**
     * Gets the {@link String} that identifies the target
     * branch.
     * 
     * @return A {@link String}, or {@code null}
     *         if {@link #isSeed() isSeed}{@code () == true}.
     */
    public String getTargetBranch() {
        return this.targetBranch;
    }

    /**
     * Gets the string literals gathered during the
     * execution of the path to the frontier.
     * 
     * @return A {@link Map}{@code <}{@link Long}{@code , }{@link String}{@code >}, or {@code null}
     *         if {@link #isSeed() isSeed}{@code () == true}.
     */
    public Map<Long, String> getStringLiterals() {
        return this.stringLiterals;
    }

    /**
     * Gets the (nonconstant) strings gathered during the
     * execution of the path to the frontier.
     * 
     * @return A {@link List}{@code <}{@link Long}{@code >}, or {@code null}
     *         if {@link #isSeed() isSeed}{@code () == true}.
     */
    public Set<Long> getStringOthers() {
        return this.stringOthers;
    }

    /**
     * Gets the depth of the path to the frontier
     * 
     * @return A positive {@code int}, or {@code -1}
     *         if {@link #isSeed() isSeed}{@code () == true}.
     */
    public int getDepth() {
        return this.depth;
    }	
    
    public int getNoveltyIndex() {
    	return this.noveltyIndex;
    }

    public void setNoveltyIndex(int newNoveltyIndex) {
    	this.noveltyIndex = newNoveltyIndex;
    }
    
    public int getImprovabilityIndex() {
    	return this.improvabilityIndex;
    }
    
    public void setImprovabilityIndex(int newImprovabilityIndex) {
    	this.improvabilityIndex = newImprovabilityIndex;
    }
    
    public List<List<Clause>> getNotCoveredBranches() {
    	return this.notCoveredBranches;
    }
    
    public void setNotCoveredBranches(List<List<Clause>> newNotCoveredBranches) {
    	this.notCoveredBranches = newNotCoveredBranches;
    }
}
