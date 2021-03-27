package tardis.implementation;

import static jbse.algo.Util.valueString;
import static jbse.bc.ClassLoaders.CLASSLOADER_BOOT;
import static jbse.bc.Signatures.JAVA_OBJECT;
import static jbse.bc.Signatures.JAVA_STRING;
import static tardis.implementation.Util.bytecodeJump;
import static tardis.implementation.Util.bytecodeLoad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jbse.algo.exc.CannotManageStateException;
import jbse.algo.exc.NotYetImplementedException;
import jbse.bc.ClassFile;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.common.exc.ClasspathException;
import jbse.common.exc.InvalidInputException;
import jbse.dec.exc.DecisionException;
import jbse.jvm.Engine;
import jbse.jvm.Runner;
import jbse.jvm.RunnerBuilder;
import jbse.jvm.RunnerParameters;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.jvm.Runner.Actions;
import jbse.mem.Clause;
import jbse.mem.ClauseAssume;
import jbse.mem.ClauseAssumeAliases;
import jbse.mem.ClauseAssumeClassInitialized;
import jbse.mem.ClauseAssumeClassNotInitialized;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.ClauseAssumeNull;
import jbse.mem.ClauseAssumeReferenceSymbolic;
import jbse.mem.Objekt;
import jbse.mem.State;
import jbse.mem.State.Phase;
import jbse.mem.exc.CannotAssumeSymbolicObjectException;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.FrozenStateException;
import jbse.mem.exc.HeapMemoryExhaustedException;
import jbse.mem.exc.InvalidNumberOfOperandsException;
import jbse.mem.exc.ThreadStackEmptyException;
import jbse.tree.StateTree.BranchPoint;
import jbse.val.Calculator;
import jbse.val.Reference;
import jbse.val.ReferenceConcrete;
import jbse.val.ReferenceSymbolic;
import jbse.val.Value;

/**
 * Encapsulates a {@link Runner} that runs a test from a frontier up to the next level of depth, 
 * for all its possible branches.
 * 
 * @author Pietro Braione
 *
 */
final class RunnerPostFrontier implements AutoCloseable {
    private static final Logger LOGGER = LogManager.getFormatterLogger(RunnerPostFrontier.class);

    private final Runner runnerPostFrontier;
    private final Map<Long, String> stringLiteralsAtFrontier;
    private final Set<Long> stringOthersAtFrontier;
    private final ArrayList<HashMap<Long, String>> stringLiterals = new ArrayList<>();
    private final ArrayList<HashSet<Long>> stringOthers = new ArrayList<>();
    private final ArrayList<State> statesPostFrontier = new ArrayList<>();
    private final ArrayList<String> branchesPostFrontier = new ArrayList<>();
    private final HashSet<String> coverage = new HashSet<>();
    private int testDepth;
    private HashMap<Long, String> stringLiteralsCurrent;
    private HashSet<Long> stringOthersCurrent;
    private boolean firstPostFrontierToDo = true;
    private boolean contradictory = false;
    private boolean atJump = false;
    private int jumpPC = 0;
    private boolean atLoadConstant = false;
    private int loadConstantStackSize = 0;
    
    public RunnerPostFrontier(RunnerParameters runnerParameters, Map<Long, String> stringLiterals, Set<Long> stringOthers) 
    throws NotYetImplementedException, CannotBuildEngineException, DecisionException, InitializationException, 
    InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, ClasspathException, 
    ContradictionException {
    	runnerParameters.setActions(new ActionsRunnerPostFrontier());
        final RunnerBuilder rb = new RunnerBuilder();
        this.runnerPostFrontier = rb.build(runnerParameters);
        this.stringLiteralsAtFrontier = stringLiterals;
        this.stringOthersAtFrontier = stringOthers;
        this.stringLiteralsCurrent = new HashMap<>(this.stringLiteralsAtFrontier);
        this.stringOthersCurrent = new HashSet<>(this.stringOthersAtFrontier);
    }
    
    public void setTestDepth(int testDepth) {
        this.testDepth = testDepth;
    }
    
    public void run() throws CannotBacktrackException, CannotManageStateException, ClasspathException, 
    ThreadStackEmptyException, ContradictionException, DecisionException, EngineStuckException, 
    FailureException, NonexistingObservedVariablesException {
    	this.runnerPostFrontier.run();
    }
    
    public List<State> getStatesPostFrontier() {
    	return this.statesPostFrontier;
    }
    
    public boolean atJump() {
    	return this.atJump;
    }
    
    public List<String> getBranchesPostFrontier() {
    	return this.branchesPostFrontier;
    }
    
    public List<? extends Map<Long, String>> getStringLiterals() {
    	return this.stringLiterals;
    }
    
    public List<? extends Set<Long>> getStringOthers() {
    	return this.stringOthers;
    }
    
    public Set<String> getCoverage() {
    	return this.coverage;
    }

    /**
     * The {@link Actions} for this {@link RunnerPostFrontier}.
     * 
     * @author Pietro Braione
     *
     */
    private class ActionsRunnerPostFrontier extends Actions {
        @Override
        public boolean atStepPre() {
            final State currentState = getEngine().getCurrentState();
            if (currentState.phase() != Phase.PRE_INITIAL) {
                try {
                    final byte currentInstruction = currentState.getInstruction();

                    //if at a jump bytecode, saves the start program counter
                    RunnerPostFrontier.this.atJump = bytecodeJump(currentInstruction);
                    if (RunnerPostFrontier.this.atJump) {
                    	RunnerPostFrontier.this.jumpPC = currentState.getCurrentProgramCounter();
                    }

                    //if at a load constant bytecode, saves the stack size
                    RunnerPostFrontier.this.atLoadConstant = bytecodeLoad(currentInstruction);
                    if (RunnerPostFrontier.this.atLoadConstant) {
                    	RunnerPostFrontier.this.loadConstantStackSize = currentState.getStackSize();
                    }
                } catch (ThreadStackEmptyException | FrozenStateException e) {
                    //this should never happen
                    LOGGER.error("Internal error when attempting to inspect the current bytecode instruction");
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
            
            //updates coverage
            if (currentState.phase() != Phase.PRE_INITIAL && RunnerPostFrontier.this.atJump) {
                try {
                	RunnerPostFrontier.this.coverage.add(currentState.getCurrentMethodSignature().toString() + ":" + RunnerPostFrontier.this.jumpPC + ":" + currentState.getCurrentProgramCounter());
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

            //saves string constants
            if (currentState.phase() != Phase.PRE_INITIAL && RunnerPostFrontier.this.atLoadConstant) {
                possiblySaveStringConstant(currentState);
            }

            if (currentState.getDepth() == RunnerPostFrontier.this.testDepth + 1) {
                //we are at a post-frontier state (including the first one)
                recordState(currentState);
                
                //if some references were partially resolved adds states with expansion
                //clauses in the path condition, hoping that EvoSuite will discover a
                //concrete class for them
                if (RunnerPostFrontier.this.firstPostFrontierToDo && getEngine().someReferencePartiallyResolved()) {
                    recordStatesForExpansion(currentState);
                }
                RunnerPostFrontier.this.firstPostFrontierToDo = false;
                
                getEngine().stopCurrentPath();
            }
            
            return super.atStepPost();
        }

        @Override
        public boolean atBacktrackPost(BranchPoint bp) {
            final State currentState = getEngine().getCurrentState();
            
            //updates coverage
            if (currentState.phase() != Phase.PRE_INITIAL && RunnerPostFrontier.this.atJump) {
                try {
                	RunnerPostFrontier.this.coverage.add(currentState.getCurrentMethodSignature().toString() + ":" + RunnerPostFrontier.this.jumpPC + ":" + currentState.getCurrentProgramCounter());
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
            
            if (currentState.getDepth() == RunnerPostFrontier.this.testDepth + 1) {
                //we are at a post-frontier state (excluding the first one)
                recordState(currentState);
                getEngine().stopCurrentPath();            
                return super.atBacktrackPost(bp);
            } else {
                //we are at a lesser depth than testDepth + 1: no
                //more post-frontier states
                return true;
            }
        }
        
        @Override
        public boolean atContradictionException(ContradictionException e) {
        	RunnerPostFrontier.this.contradictory = true;
            return false;
        }
        
        private void recordState(State s) {
            if (!RunnerPostFrontier.this.contradictory) {
            	RunnerPostFrontier.this.statesPostFrontier.add(s.clone());
            	RunnerPostFrontier.this.stringLiterals.add(RunnerPostFrontier.this.stringLiteralsCurrent);
            	RunnerPostFrontier.this.stringOthers.add(RunnerPostFrontier.this.stringOthersCurrent);
            	RunnerPostFrontier.this.stringLiteralsCurrent = new HashMap<>(RunnerPostFrontier.this.stringLiteralsAtFrontier);
            	RunnerPostFrontier.this.stringOthersCurrent = new HashSet<>(RunnerPostFrontier.this.stringOthersAtFrontier);
                if (RunnerPostFrontier.this.atJump) {
                    try {
                    	RunnerPostFrontier.this.branchesPostFrontier.add(s.getCurrentMethodSignature().toString() + ":" + RunnerPostFrontier.this.jumpPC + ":" + s.getCurrentProgramCounter());
                    } catch (ThreadStackEmptyException e) {
                        //this should never happen
                        throw new RuntimeException(e); //TODO better exception!
                    }
                } //else, do nothing
            }
            RunnerPostFrontier.this.contradictory = false;
        }
        
        private void possiblySaveStringConstant(State currentState) {
            try {
                if (RunnerPostFrontier.this.loadConstantStackSize == currentState.getStackSize()) {
                    final Value operand = currentState.getCurrentFrame().operands(1)[0];
                    if (operand instanceof Reference) {
                        final Reference r = (Reference) operand;
                        final Objekt o = currentState.getObject(r);
                        if (o != null && JAVA_STRING.equals(o.getType().getClassName())) {
                            final long heapPosition = (r instanceof ReferenceConcrete ? ((ReferenceConcrete) r).getHeapPosition() : currentState.getResolution((ReferenceSymbolic) r));
                            final String s = valueString(currentState, r);
                            if (s == null) {
                            	RunnerPostFrontier.this.stringOthersCurrent.add(heapPosition);
                            } else {
                            	RunnerPostFrontier.this.stringLiteralsCurrent.put(heapPosition, s);
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
        
        private void recordStatesForExpansion(State currentState) {
            final Engine engine = getEngine();
            final Calculator calc = engine.getExecutionContext().getCalculator();
            final ClassFile cfJAVA_OBJECT = currentState.getClassHierarchy().getClassFileClassArray(CLASSLOADER_BOOT, JAVA_OBJECT);
            final List<ReferenceSymbolic> partiallyResolvedReferences = engine.getPartiallyResolvedReferences();
            LOGGER.warn("Reference%s %s %s partially resolved, artificially generating constraints for attempting their expansion",
            (partiallyResolvedReferences.size() == 1 ? "" : "s"),
            String.join(", ", partiallyResolvedReferences.stream().map(ReferenceSymbolic::asOriginString).toArray(String[]::new)),
            (partiallyResolvedReferences.size() == 1 ? "was" : "were"));
            
            for (ReferenceSymbolic partiallyResolvedReference : partiallyResolvedReferences) {
                final State stateForExpansion = engine.getExecutionContext().getStateStart();
                final int commonClauses = stateForExpansion.getPathCondition().size();
                int currentClause = 0;
                for (Clause c : currentState.getPathCondition()) {
                    try {
                        ++currentClause;
                        if (currentClause <= commonClauses) {
                            continue;
                        }
                        if (c instanceof ClauseAssumeReferenceSymbolic && 
                        ((ClauseAssumeReferenceSymbolic) c).getReference().equals(partiallyResolvedReference)) {
                            stateForExpansion.assumeExpands(calc, partiallyResolvedReference, cfJAVA_OBJECT);
                        } else if (c instanceof ClauseAssume) {
                            stateForExpansion.assume(((ClauseAssume) c).getCondition());
                        } else if (c instanceof ClauseAssumeClassInitialized) {
                            final ClauseAssumeClassInitialized cc = (ClauseAssumeClassInitialized) c;
                            if (stateForExpansion.getKlass(cc.getClassFile()) == null) {
                                stateForExpansion.ensureKlassSymbolic(calc, cc.getClassFile());
                            }
                            stateForExpansion.assumeClassInitialized(cc.getClassFile(), stateForExpansion.getKlass(cc.getClassFile()));
                        } else if (c instanceof ClauseAssumeClassNotInitialized) {
                            stateForExpansion.assumeClassNotInitialized(((ClauseAssumeClassNotInitialized) c).getClassFile());
                        } else if (c instanceof ClauseAssumeNull) {
                            stateForExpansion.assumeNull(((ClauseAssumeNull) c).getReference());
                        } else if (c instanceof ClauseAssumeExpands) {
                            final ClauseAssumeExpands cc = (ClauseAssumeExpands) c;
                            stateForExpansion.assumeExpands(calc, cc.getReference(), cc.getObjekt().getType());
                        } else if (c instanceof ClauseAssumeAliases) {
                            final ClauseAssumeAliases cc = (ClauseAssumeAliases) c;
                            stateForExpansion.assumeAliases(cc.getReference(), cc.getObjekt().getOrigin());
                        }
                    } catch (CannotAssumeSymbolicObjectException | InvalidInputException |
                    ContradictionException e) {
                        LOGGER.error("Internal error when attempting to artificially generate an expansion constraint");
                        LOGGER.error("Message: %s", e.toString());
                        LOGGER.error("Stack trace:");
                        for (StackTraceElement elem : e.getStackTrace()) {
                            LOGGER.error("%s", elem.toString());
                        }
                        throw new RuntimeException(e); //TODO throw better exception
                    } catch (HeapMemoryExhaustedException e) {
                        LOGGER.error("Symbolic execution heap memory exhausted when attempting to artificially generate an expansion constraint");
                        throw new RuntimeException(e); //TODO throw better exception
                    }
                }
                recordState(stateForExpansion);
            }
        }
    }
    
    @Override
    public void close() throws DecisionException {
    	this.runnerPostFrontier.getEngine().close();
    }
}
