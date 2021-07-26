package tardis.implementation.jbse;

import static jbse.algo.Util.valueString;
import static jbse.bc.Signatures.JAVA_STRING;
import static tardis.implementation.common.Util.bytecodeBranch;
import static tardis.implementation.common.Util.bytecodeJump;
import static tardis.implementation.common.Util.bytecodeLoad;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jbse.algo.exc.CannotManageStateException;
import jbse.algo.exc.NotYetImplementedException;
import jbse.apps.run.DecisionProcedureGuidance;
import jbse.apps.run.GuidanceException;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.common.exc.ClasspathException;
import jbse.dec.exc.DecisionException;
import jbse.jvm.Runner;
import jbse.jvm.RunnerBuilder;
import jbse.jvm.Runner.Actions;
import jbse.jvm.RunnerParameters;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.mem.Objekt;
import jbse.mem.State;
import jbse.mem.State.Phase;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.FrozenStateException;
import jbse.mem.exc.InvalidNumberOfOperandsException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.val.Reference;
import jbse.val.ReferenceConcrete;
import jbse.val.ReferenceSymbolic;
import jbse.val.Value;

/**
 * Encapsulates a {@link Runner} that runs a test up to a frontier, i.e., up to some depth, 
 * guided by some concrete execution.
 * 
 * @author Pietro Braione
 *
 */
final class RunnerPreFrontier implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getFormatterLogger(RunnerPreFrontier.class);

    private final Runner runner;
    private final DecisionProcedureGuidance guid;
    private final long maxCount;
    private final HashMap<Long, String> stringLiterals = new HashMap<>();
    private final HashSet<Long> stringOthers = new HashSet<>();
    private final HashSet<String> coverage = new HashSet<>();
    private int postFrontierDepth = 0;
    private boolean atJump = false;
    private int jumpPC = 0;
    private boolean atLoadConstant = false;
    private int loadConstantStackSize = 0;
    private boolean foundPreFrontier = false;
    private State preFrontierState;

    public RunnerPreFrontier(RunnerParameters runnerParameters, long maxCount) 
    throws NotYetImplementedException, CannotBuildEngineException, DecisionException, 
    InitializationException, InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
    ClasspathException, ContradictionException {
    	runnerParameters.setActions(new ActionsRunnerPreFrontier());
        final RunnerBuilder rb = new RunnerBuilder();
        this.runner = rb.build(runnerParameters);
        this.guid = (DecisionProcedureGuidance) runnerParameters.getDecisionProcedure();
        this.maxCount = maxCount;
    }
    
    /**
     * Sets the pre-frontier depth.
     * 
     * @param postFrontierDepth an {@code int}. If 0 stops
     *        at the initial state. If greater than zero, 
     *        stops at the frontier between {@code postFrontierDepth - 1}
     *        and {@code postFrontierDepth}. 
     */
    public void setPostFrontierDepth(int postFrontierDepth) {
        this.postFrontierDepth = postFrontierDepth;
    }
    
    public State getInitialState() {
    	return this.runner.getEngine().getInitialState();
    }
    
    public State getCurrentState() {
    	return this.runner.getEngine().getCurrentState();
    }
    
    public boolean foundPreFrontier() {
    	return this.foundPreFrontier;
    }
    
    public State getPreFrontierState() {
    	return this.preFrontierState;
    }
    
    public Map<Long, String> getStringLiterals() {
    	return this.stringLiterals;
    }
    
    public Set<Long> getStringOthers() {
    	return this.stringOthers;
    }
    
    public Set<String> getCoverage() {
    	return this.coverage;
    }

    public void run() 
    throws CannotBacktrackException, CannotManageStateException, ClasspathException, 
    ThreadStackEmptyException, ContradictionException, DecisionException, EngineStuckException, 
    FailureException, NonexistingObservedVariablesException {
    	this.runner.run();
    }
    
    /**
     * The {@link Actions} for this {@link RunnerPreFrontier}.
     * 
     * @author Pietro Braione
     *
     */
    private class ActionsRunnerPreFrontier extends Actions {
        @Override
        public boolean atInitial() {
            if (RunnerPreFrontier.this.postFrontierDepth == 0) {
                return true;
            } else {
                return super.atInitial();
            }
        }

        @Override
        public boolean atStepPre() {
            final State currentState = getEngine().getCurrentState();
            if (currentState.phase() != Phase.PRE_INITIAL) {
                try {
                    final int currentProgramCounter = currentState.getCurrentProgramCounter();
                    final byte currentInstruction = currentState.getInstruction();
                    
                    //if at entry of a method, add the entry point to coverage 
                    if (currentProgramCounter == 0) {
                    	RunnerPreFrontier.this.coverage.add(currentState.getCurrentMethodSignature().toString() + ":0:0");
                    }

                    //if at a jump bytecode, saves the start program counter
                    RunnerPreFrontier.this.atJump = bytecodeJump(currentInstruction);
                    if (RunnerPreFrontier.this.atJump) {
                    	RunnerPreFrontier.this.jumpPC = currentProgramCounter;
                    }

                    //if at a load constant bytecode, saves the stack size
                    RunnerPreFrontier.this.atLoadConstant = bytecodeLoad(currentInstruction);
                    if (RunnerPreFrontier.this.atLoadConstant) {
                    	RunnerPreFrontier.this.loadConstantStackSize = currentState.getStackSize();
                    }
                    
                    //if at a symbolic branch bytecode, and at postFrontierDepth - 1,
                    //saves the pre-state
                	if (bytecodeBranch(currentInstruction) && currentState.getDepth() == RunnerPreFrontier.this.postFrontierDepth - 1) {
                        RunnerPreFrontier.this.preFrontierState = currentState.clone();
                	}
                } catch (ThreadStackEmptyException | FrozenStateException e) {
                    //this should never happen
                    LOGGER.error("Internal error when attempting to inspect the state before bytecode instruction execution");
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    throw new RuntimeException(e); //TODO throw better exception
                }
            }
            
            return super.atStepPre();
        }

        @Override
        public boolean atStepPost() {
            final State currentState = getEngine().getCurrentState();

            //steps guidance
            try {
            	RunnerPreFrontier.this.guid.step(currentState);
            } catch (GuidanceException e) {
                throw new RuntimeException(e); //TODO better exception!
            }
            
            //updates coverage
            if (currentState.phase() != Phase.PRE_INITIAL && RunnerPreFrontier.this.atJump) {
                try {
                	RunnerPreFrontier.this.coverage.add(currentState.getCurrentMethodSignature().toString() + ":" + RunnerPreFrontier.this.jumpPC + ":" + currentState.getCurrentProgramCounter());
                } catch (ThreadStackEmptyException e) {
                    //this should never happen
                    LOGGER.error("Internal error when attempting to update coverage");
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    throw new RuntimeException(e); //TODO throw better exception
                }
            }

            //stops if current state is at post-frontier (before adding string literals)
            RunnerPreFrontier.this.foundPreFrontier = (currentState.getDepth() == RunnerPreFrontier.this.postFrontierDepth);
            if (RunnerPreFrontier.this.foundPreFrontier || currentState.getCount() >= RunnerPreFrontier.this.maxCount) {
                return true;
            }

            //manages string literals (they might be useful to EvoSuite)
            if (currentState.phase() != Phase.PRE_INITIAL && RunnerPreFrontier.this.atLoadConstant) {
                try {
                    if (RunnerPreFrontier.this.loadConstantStackSize == currentState.getStackSize()) {
                        final Value operand = currentState.getCurrentFrame().operands(1)[0];
                        if (operand instanceof Reference) {
                            final Reference r = (Reference) operand;
                            final Objekt o = currentState.getObject(r);
                            if (o != null && JAVA_STRING.equals(o.getType().getClassName())) {
                                final long heapPosition = (r instanceof ReferenceConcrete ? ((ReferenceConcrete) r).getHeapPosition() : currentState.getResolution((ReferenceSymbolic) r));
                                final String s = valueString(currentState, r);
                                if (s == null) {
                                	RunnerPreFrontier.this.stringOthers.add(heapPosition);
                                } else {
                                	RunnerPreFrontier.this.stringLiterals.put(heapPosition, s);
                                }
                            } //TODO: constants for Integer, Float, Double ... boxed types; add generic stateful object graphs produced by pure methods
                        }					
                    }
                } catch (FrozenStateException | InvalidNumberOfOperandsException | ThreadStackEmptyException e) {
                    //this should never happen
                    LOGGER.error("Internal error when attempting to manage String literals");
                    LOGGER.error("Message: %s", e.toString());
                    LOGGER.error("Stack trace:");
                    for (StackTraceElement elem : e.getStackTrace()) {
                        LOGGER.error("%s", elem.toString());
                    }
                    throw new RuntimeException(e); //TODO throw better exception
                }
            }

            return super.atStepPost();
        }

        @Override
        public boolean atPathEnd() {
        	//this triggers end of unconstrained exploration when
        	//the path is shorter than the depth bound
        	RunnerPreFrontier.this.foundPreFrontier = false;
        	return true;
        }
        
        @Override
        public boolean atContradictionException(ContradictionException e) {
        	RunnerPreFrontier.this.foundPreFrontier = false;
            return true;
        }
    }
    
    @Override
    public void close() throws DecisionException {
    	this.runner.getEngine().close();
    }
}
