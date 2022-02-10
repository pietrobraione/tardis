package tardis.implementation.jbse;

import static jbse.apps.run.DecisionProcedureGuidanceJDI.countNonRecursiveHits;
import static jbse.bc.Signatures.JAVA_CHARSEQUENCE;
import static jbse.bc.Signatures.JAVA_OBJECT;
import static jbse.bc.Signatures.JAVA_STRING;
import static jbse.common.Type.BOOLEAN;
import static jbse.common.Type.REFERENCE;
import static jbse.common.Type.TYPEEND;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jbse.algo.exc.CannotManageStateException;
import jbse.algo.exc.NotYetImplementedException;
import jbse.apps.run.DecisionProcedureGuidanceJDI;
import jbse.bc.Signature;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.common.exc.ClasspathException;
import jbse.common.exc.InvalidInputException;
import jbse.dec.DecisionProcedureAlgorithms;
import jbse.dec.DecisionProcedureAlwSat;
import jbse.dec.DecisionProcedureClassInit;
import jbse.dec.DecisionProcedureLICS;
import jbse.dec.DecisionProcedureSMTLIB2_AUFNIRA;
import jbse.dec.exc.DecisionException;
import jbse.jvm.RunnerParameters;
import jbse.jvm.EngineParameters.BreadthMode;
import jbse.jvm.EngineParameters.StateIdentificationMode;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.rewr.CalculatorRewriting;
import jbse.rewr.RewriterNegationElimination;
import jbse.rewr.RewriterExpressionOrConversionOnSimplex;
import jbse.rewr.RewriterFunctionApplicationOnSimplex;
import jbse.rewr.RewriterZeroUnit;
import jbse.rules.ClassInitRulesRepo;
import jbse.rules.LICSRulesRepo;
import tardis.Options;
import tardis.implementation.evosuite.EvosuiteResult;
import tardis.implementation.evosuite.TestCase;

/**
 * A class that exploits JBSE to perform guided symbolic execution
 * of a test and possibly explore its frontier at multiple depths.
 *  
 * @author Pietro Braione
 */
final class RunnerPath implements AutoCloseable {
    private static final String SWITCH_CHAR = System.getProperty("os.name").toLowerCase().contains("windows") ? "/" : "-";

    private final String z3Path;
    private final String targetMethodClassName;
    private final String targetMethodDescriptor;
    private final String targetMethodName;
    private final TestCase testCase;
    private final int maxDepth;
    private final long maxCount;
    private final RunnerParameters commonParamsSymbolic;
    private final RunnerParameters commonParamsConcrete;
    private final int numberOfHits;
    private RunnerPreFrontier runnerPreFrontier = null;
    private State statePreFrontier = null;
    private RunnerPostFrontier runnerPostFrontier = null;
    
    public RunnerPath(Options o, EvosuiteResult item, State initialState) 
    throws DecisionException, CannotBuildEngineException, InitializationException, InvalidClassFileFactoryClassException, 
    NonexistingObservedVariablesException, ClasspathException, ContradictionException, CannotBacktrackException, 
    CannotManageStateException, ThreadStackEmptyException, EngineStuckException, FailureException, NoTargetHitException {
        this.z3Path = o.getZ3Path().toString();
        this.targetMethodClassName = item.getTargetMethodClassName();
        this.targetMethodDescriptor = item.getTargetMethodDescriptor();
        this.targetMethodName = item.getTargetMethodName();
        this.testCase = item.getTestCase();
        this.maxDepth = o.getMaxDepth();
        this.maxCount = o.getMaxCount();

        //builds the template parameters object for the guided (symbolic) 
        //and the guiding (concrete) executions
        this.commonParamsSymbolic = new RunnerParameters();
        this.commonParamsConcrete = new RunnerParameters();
        fillCommonParams(o, item, initialState);

        
        //calculates the number of hits
        this.numberOfHits = countNumberOfInvocations(this.targetMethodClassName, this.targetMethodDescriptor, this.targetMethodName);
        if (this.numberOfHits == 0) {
            throw new NoTargetHitException();
        }
    }
    
    private void fillCommonParams(Options o, EvosuiteResult item, State initialState) {
        final ArrayList<String> _classpath = new ArrayList<>();
        _classpath.add(o.getEvosuitePath().toString());
        _classpath.add(o.getTmpBinDirectoryPath().toString());
        _classpath.addAll(o.getClassesPath().stream().map(Object::toString).collect(Collectors.toList()));
        //builds the template parameters object for the guided (symbolic) execution
        if (initialState == null) {
            this.commonParamsSymbolic.setJBSELibPath(o.getJBSELibraryPath());
            this.commonParamsSymbolic.addUserClasspath(_classpath.toArray(new String[0]));
            this.commonParamsSymbolic.setMethodSignature(item.getTargetMethodClassName(), item.getTargetMethodDescriptor(), item.getTargetMethodName());
        } else {
            this.commonParamsSymbolic.setStartingState(initialState);
        }
        this.commonParamsSymbolic.setBreadthMode(BreadthMode.ALL_DECISIONS_SYMBOLIC);
        if (o.getHeapScope() != null) {
            for (Map.Entry<String, Integer> e : o.getHeapScope().entrySet()) {
                this.commonParamsSymbolic.setHeapScope(e.getKey(), e.getValue());
            }
        }
        if (o.getCountScope() > 0) {
            this.commonParamsSymbolic.setCountScope(o.getCountScope());
        }
        this.commonParamsSymbolic.addUninterpreted(JAVA_STRING, "(" + REFERENCE + JAVA_OBJECT + TYPEEND + ")" + BOOLEAN, "equals");
        this.commonParamsSymbolic.addUninterpreted(JAVA_STRING, "(" + REFERENCE + JAVA_CHARSEQUENCE + TYPEEND + ")" + BOOLEAN, "contains");
        this.commonParamsSymbolic.addUninterpreted(JAVA_STRING, "(" + REFERENCE + JAVA_STRING + TYPEEND + ")" + BOOLEAN, "endsWith");
        this.commonParamsSymbolic.addUninterpreted(JAVA_STRING, "(" + REFERENCE + JAVA_STRING + TYPEEND + ")" + BOOLEAN, "startsWith");
        for (List<String> unint : o.getUninterpreted()) {
            this.commonParamsSymbolic.addUninterpreted(unint.get(0), unint.get(1), unint.get(2));
        }

        //builds the template parameters object for the guiding (concrete) execution
        _classpath.add(o.getJBSELibraryPath().toString());
        this.commonParamsConcrete.addUserClasspath(_classpath.toArray(new String[0]));
        this.commonParamsConcrete.setStateIdentificationMode(StateIdentificationMode.COMPACT);

        //more settings to the template parameters objects:
        //1- accelerate things by bypassing standard loading
        if (initialState == null) {
            this.commonParamsSymbolic.setBypassStandardLoading(true);
        }
        this.commonParamsConcrete.setBypassStandardLoading(true); //this has no effect with JDI guidance (unfortunately introduces misalignments between the two)

        //2- disallow aliasing to static, pre-initial objects (too hard)
        this.commonParamsSymbolic.setMakePreInitClassesSymbolic(false);
        this.commonParamsConcrete.setMakePreInitClassesSymbolic(false);

        //3- set the maximum length of arrays with simple representation
        this.commonParamsSymbolic.setMaxSimpleArrayLength(o.getMaxSimpleArrayLength());
        this.commonParamsConcrete.setMaxSimpleArrayLength(o.getMaxSimpleArrayLength());

        //4- set the guiding method (to be executed concretely)
        this.commonParamsConcrete.setMethodSignature(this.testCase.getClassName(), this.testCase.getMethodDescriptor(), this.testCase.getMethodName());
        
        //5- set the executions to execute the static initializer
        this.commonParamsConcrete.addClassInvariantAfterInitializationPattern(".*");
        this.commonParamsSymbolic.addClassInvariantAfterInitializationPattern(".*");
    }

    /**
     * Performs symbolic execution of the target method guided by a test case,
     * and returns the final state.
     *
     * @return the final {@link State}, or {@code null} if there is no final 
     *         {@link State} because of a contradiction.
     * @throws DecisionException
     * @throws CannotBuildEngineException
     * @throws InitializationException
     * @throws InvalidClassFileFactoryClassException
     * @throws NonexistingObservedVariablesException
     * @throws ClasspathException
     * @throws CannotBacktrackException
     * @throws CannotManageStateException
     * @throws ThreadStackEmptyException
     * @throws ContradictionException
     * @throws EngineStuckException
     * @throws FailureException
     */
    public State runProgram()
    throws DecisionException, CannotBuildEngineException, InitializationException, 
    InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
    ClasspathException, CannotBacktrackException, CannotManageStateException, 
    ThreadStackEmptyException, ContradictionException, EngineStuckException, 
    FailureException {
        final List<State> endStates = runProgram(-1); //depth -1 == never stop guidance
        return (endStates.size() == 0 ? null : endStates.get(0));
    }
    
    /**
     * Performs symbolic execution of the target method guided by a test case 
     * up to some depth, then peeks the states on the next branch.  
     * 
     * @param testDepth the maximum depth up to which {@code t} guides 
     *        symbolic execution. When {@code testDepth == 0} the execution
     *        will stop at the initial state. When {@code testDepth < 0}
     *        the execution will completely execute the test case. For
     *        values equal or greater than 1, the execution will execute
     *        the test case up to the last branch (pre-frontier state) at depth 
     *        {@code testDepth - 1}, and then explore the post-frontier 
     *        states at depth {@code testDepth}.   
     * @return a {@link List}{@code <}{@link State}{@code >} containing
     *         all the states on branch at depth {@code testDepth}. 
     *         When {@code testDepth < 0} executes the test up to the 
     *         final state and returns a singleton list containing the final state.
     *         If the execution exhausts one of its bounds or terminates (does 
     *         not arrive at the pre-frontier branch) before the depth equals
     *         {@code testDepth}, returns an empty {@link List}.  
     * @throws DecisionException
     * @throws CannotBuildEngineException
     * @throws InitializationException
     * @throws InvalidClassFileFactoryClassException
     * @throws NonexistingObservedVariablesException
     * @throws ClasspathException
     * @throws CannotBacktrackException
     * @throws CannotManageStateException
     * @throws ThreadStackEmptyException
     * @throws ContradictionException
     * @throws EngineStuckException
     * @throws FailureException
     */
    public List<State> runProgram(int testDepth)
    throws DecisionException, CannotBuildEngineException, InitializationException, 
    InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
    ClasspathException, CannotBacktrackException, CannotManageStateException, 
    ThreadStackEmptyException, ContradictionException, EngineStuckException, 
    FailureException {
        //runs up to the pre-frontier
        if (this.runnerPreFrontier == null || 
        !this.runnerPreFrontier.foundPreFrontier() ||
        this.runnerPreFrontier.getPreFrontierState().getDepth() >= testDepth) {
            makeRunnerPreFrontier();
        }
    	final int postFrontierDepth = Math.min(this.maxDepth, testDepth);
        this.runnerPreFrontier.setPostFrontierDepth(testDepth < 0 ? this.maxDepth : postFrontierDepth);
        this.runnerPreFrontier.run();
        
        //returns the result
        if (testDepth < 0) {
        	//there is no frontier, and the runnerPreFrontier's final
        	//state is the final state of the guided execution
        	if (this.runnerPreFrontier.foundFinalState()) {
        		final State finalState = this.runnerPreFrontier.getCurrentState().clone();
        		return Collections.singletonList(finalState);
        	} else {
        		//maxDepth was too shallow
        		return Collections.emptyList();
        	}
        } else if (this.runnerPreFrontier.foundPreFrontier()) {
        	//steps to all the post-frontier states and gathers them
        	this.statePreFrontier = this.runnerPreFrontier.getPreFrontierState().clone();
        	makeRunnerPostFrontier();
        	if (this.runnerPostFrontier == null) {
        		return Collections.emptyList();
        	} else {
        		this.runnerPostFrontier.setPostFrontierDepth(postFrontierDepth);
        		this.runnerPostFrontier.run();
        		return this.runnerPostFrontier.getStatesPostFrontier();
        	}
        } else {
        	return Collections.emptyList();
        }
    }
    
    private void makeRunnerPreFrontier() throws DecisionException, NotYetImplementedException, 
    CannotBuildEngineException, InitializationException, InvalidClassFileFactoryClassException, 
    NonexistingObservedVariablesException, ClasspathException, ContradictionException {
        //builds the parameters
        final RunnerParameters pSymbolic = this.commonParamsSymbolic.clone();
        final RunnerParameters pConcrete = this.commonParamsConcrete.clone();
        completeParametersGuided(pSymbolic, pConcrete);
        
        //builds the runner
        this.runnerPreFrontier = new RunnerPreFrontier(pSymbolic, this.maxCount);
    }
    
    private void makeRunnerPostFrontier() throws DecisionException, NotYetImplementedException, 
    CannotBuildEngineException, InitializationException, InvalidClassFileFactoryClassException, 
    NonexistingObservedVariablesException, ClasspathException, ContradictionException {
        //gets the pre-frontier state and sets it as the initial
        //state of the post-frontier runner
        if (this.statePreFrontier.isStuck()) {
            //degenerate case: execution ended before or at the pre-frontier
            this.runnerPostFrontier = null;
        } else { 
            //builds the parameters
            final RunnerParameters pSymbolic = this.commonParamsSymbolic.clone();
            completeParametersSymbolic(pSymbolic);            
            pSymbolic.setStartingState(this.statePreFrontier);

            //builds the runner
            this.runnerPostFrontier = new RunnerPostFrontier(pSymbolic, this.maxCount, this.runnerPreFrontier.getStringLiterals(), this.runnerPreFrontier.getStringOthers());
        }
    }

    private void completeParametersConcrete(RunnerParameters pConcrete) throws DecisionException {
        completeParametersGuided(null, pConcrete);
    }

    private void completeParametersSymbolic(RunnerParameters pSymbolic) throws DecisionException {
        completeParametersGuided(pSymbolic, null);
    }

    private void completeParametersGuided(RunnerParameters pSymbolic, RunnerParameters pConcrete) throws DecisionException {
        //sets the calculator
        final CalculatorRewriting calc = new CalculatorRewriting();
        calc.addRewriter(new RewriterExpressionOrConversionOnSimplex());
        calc.addRewriter(new RewriterFunctionApplicationOnSimplex());
        calc.addRewriter(new RewriterZeroUnit());
        calc.addRewriter(new RewriterNegationElimination());
        if (pConcrete == null) {
            //nothing
        } else {
            pConcrete.setCalculator(calc);
        }
        if (pSymbolic == null) {
            //nothing
        } else {
            pSymbolic.setCalculator(calc);
            pSymbolic.setUseHashMapModel(true);
        }

        //sets the decision procedures
        final ArrayList<String> z3CommandLine = new ArrayList<>();
        z3CommandLine.add(this.z3Path);
        z3CommandLine.add(SWITCH_CHAR + "smt2");
        z3CommandLine.add(SWITCH_CHAR + "in");
        z3CommandLine.add(SWITCH_CHAR + "t:100");
        final ClassInitRulesRepo initRules = new ClassInitRulesRepo();
        try {
            if (pConcrete == null) {
                //nothing
            } else {
                final DecisionProcedureAlgorithms decGuiding = 
                    new DecisionProcedureAlgorithms(
                        new DecisionProcedureClassInit(
                            new DecisionProcedureAlwSat(calc), initRules));
                pConcrete.setDecisionProcedure(decGuiding);
            }
            if (pSymbolic == null) {
                //nothing
            } else {
                final DecisionProcedureAlgorithms decAlgo = 
                    new DecisionProcedureAlgorithms(
                        new DecisionProcedureClassInit(
                            new DecisionProcedureLICS( //useless?
                                new DecisionProcedureSMTLIB2_AUFNIRA(
                                    new DecisionProcedureAlwSat(calc), z3CommandLine), 
                                new LICSRulesRepo()), initRules)); 
                if (pConcrete == null) {
                    pSymbolic.setDecisionProcedure(decAlgo);
                } else {
                    final Signature stopSignature = (pSymbolic.getMethodSignature() == null ? pSymbolic.getStartingState().getRootMethodSignature() : pSymbolic.getMethodSignature());
                    final DecisionProcedureGuidanceJDI decGuided = 
                        new DecisionProcedureGuidanceJDI(decAlgo, calc, pConcrete, stopSignature, this.numberOfHits);
                    pSymbolic.setDecisionProcedure(decGuided);
                }
            }
        } catch (InvalidInputException | ThreadStackEmptyException e) {
            //this should never happen
            throw new AssertionError(e);
        }
    }

    private int countNumberOfInvocations(String methodClassName, String methodDescriptor, String methodName)
    throws CannotBuildEngineException, DecisionException, InitializationException, InvalidClassFileFactoryClassException, 
    NonexistingObservedVariablesException, ClasspathException, ContradictionException, CannotBacktrackException, 
    CannotManageStateException, ThreadStackEmptyException, EngineStuckException, FailureException {
        final RunnerParameters pConcrete = this.commonParamsConcrete.clone();
        completeParametersConcrete(pConcrete);        
        return countNonRecursiveHits(pConcrete, new Signature(methodClassName, methodDescriptor, methodName));
    }

    /**
     * Must be invoked after an invocation of {@link #runProgram(int) runProgram(depth)} or
     * {@link #runProgram()}.
     * Returns the initial state of symbolic execution.
     * 
     * @return a {@link State} or {@code null} if this method is invoked
     *         before an invocation of {@link #runProgram(int)}.
     */
    public State getStateInitial() {
        State retVal = this.commonParamsSymbolic.getStartingState();
        if (retVal == null) {
        	if (this.runnerPreFrontier == null) {
        		retVal = null;
        	} else {
        		retVal = this.runnerPreFrontier.getInitialState();
        		this.commonParamsSymbolic.setStartingState(retVal);
        	}
        }
        return retVal;
    }

    /**
     * Must be invoked after an invocation of {@link #runProgram(int) runProgram(depth)}.
     * Returns the pre-frontier state of symbolic execution, i.e., the state at depth 
     * {@code depth}.
     * 
     * @return a {@link State}. If this method is invoked
     *         before an invocation of {@link #runProgram(int)},
     *         or if the execution does not reach the frontier,
     *         returns {@code null}.
     */
    public State getStatePreFrontier() {
        return this.statePreFrontier;
    }

    /**
     * Must be invoked after an invocation of {@link #runProgram(int) runProgram(depth)}.
     * Returns whether the frontier is at a jump bytecode.
     * 
     * @return a {@code boolean}. If this method is invoked
     *         before an invocation of {@link #runProgram(int)}, 
     *         or if the execution does not reach the frontier,
     *         returns {@code false}.
     */
    public boolean getAtJump() {
        return (this.runnerPostFrontier == null ? false : this.runnerPostFrontier.atJump());
    }

    /**
     * Must be invoked after an invocation of {@link #runProgram(int) runProgram(depth)}.
     * Returns the branches covered by the post-frontier states, i.e., the states at depth {@code depth + 1}.
     * 
     * @return a {@link List}{@code <}{@link String}{@code >} if {@link #getAtJump() getAtJump}{@code () == true}. 
     *         In this case {@code getTargetBranches().}{@link List#get(int) get}{@code (i)} is the branch
     *         covered by the {@code i}-th state in the list returned by {@link #runProgram(int) runProgram(depth)}.
     *         If  {@link #getAtJump() getAtJump}{@code () == false}, or if the method is
     *         invoked before an invocation of {@link #runProgram(int)}, 
     *         or if the execution does not reach the frontier,
     *         returns an empty {@link List}.
     */
    public List<String> getBranchesPostFrontier() {
        return (this.runnerPostFrontier == null ? Collections.emptyList() : this.runnerPostFrontier.getBranchesPostFrontier());
    }

    /**
     * Must be invoked after an invocation of {@link #runProgram(int) runProgram(depth)}.
     * Returns the string literals of the execution.
     * 
     * @return a {@link List}{@code <? extends }{@link Map}{@code <}{@link Long}{@code , }{@link String}{@code >}{@code >}. 
     *         Each element in the list maps a heap position of a {@link String} literal to the
     *         corresponding value of the literal. The i-th element in the list
     *         is for the i-th state returned by {@link #runProgram(int)}. 
     *         If this method is invoked
     *         before an invocation of {@link #runProgram(int)}  or
     *         {@link #runProgram()},
     *         or if the execution does not reach the frontier,
     *         returns an empty {@link List}.
     */
    public List<? extends Map<Long, String>> getStringLiterals() {
        return (this.runnerPostFrontier == null ? Collections.emptyList() : this.runnerPostFrontier.getStringLiterals());
    }

    /**
     * Must be invoked after an invocation of {@link #runProgram(int) runProgram(depth)}.
     * Returns the (nonconstant) strings of the execution.
     * 
     * @return a {@link List}{@code <? extends }{@link Set}{@code <}{@link Long}{@code >}{@code >}{@code >}.
     *         Each element in the list contains all the heap positions of the 
     *         (nonconstant) {@link String} objects in the heap. The i-th element
     *         in the list is for the i-th state returned by {@link #runProgram(int)}. 
     *         If this method is invoked
     *         before an invocation of {@link #runProgram(int)} or
     *         {@link #runProgram()},
     *         or if the execution does not reach the frontier,
     *         returns an empty {@link List}.
     */
    public List<? extends Set<Long>> getStringOthers() {
        return (this.runnerPostFrontier == null ? Collections.emptyList() : this.runnerPostFrontier.getStringOthers());
    }

    /**
     * Must be invoked after an invocation of {@link #runProgram(Testint) runProgram(depth)}.
     * Returns the code branches covered by the execution.
     * 
     * @return a {@link Set}{@code <}{@link String}{@code >} where each {@link String} has the form
     *         className:methodDescriptor:methodName:bytecodeFrom:bytecodeTo. If this method is invoked
     *         before an invocation of {@link #runProgram(int)} or
     *         {@link #runProgram()},
     *         or if the execution does not reach the frontier,
     *         returns an empty {@link Set}.
     */
    public Set<String> getCoverage() {
    	final HashSet<String> retVal = (this.runnerPreFrontier == null ? new HashSet<>() : new HashSet<>(this.runnerPreFrontier.getCoverage()));
    	if (this.runnerPostFrontier != null) {
    		retVal.addAll(this.runnerPostFrontier.getCoverage());
    	}
    	return retVal;
    }

    @Override
    public void close() throws DecisionException {
        if (this.runnerPreFrontier != null) {
            this.runnerPreFrontier.close();
        }
        if (this.runnerPostFrontier != null) {
            this.runnerPostFrontier.close();
        }
    }
}
