package tardis.implementation;

import static jbse.bc.ClassLoaders.CLASSLOADER_APP;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jbse.bc.ClassFile;
import jbse.bc.ClassFileFactoryJavassist;
import jbse.bc.Signature;
import jbse.bc.exc.BadClassFileVersionException;
import jbse.bc.exc.ClassFileIllFormedException;
import jbse.bc.exc.ClassFileNotAccessibleException;
import jbse.bc.exc.ClassFileNotFoundException;
import jbse.bc.exc.IncompatibleClassFileException;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.bc.exc.MethodCodeNotFoundException;
import jbse.bc.exc.MethodNotFoundException;
import jbse.bc.exc.PleaseLoadClassException;
import jbse.bc.exc.WrongClassNameException;
import jbse.common.exc.InvalidInputException;
import jbse.mem.State;
import jbse.mem.exc.CannotAssumeSymbolicObjectException;
import jbse.mem.exc.HeapMemoryExhaustedException;
import jbse.val.HistoryPoint;
import jbse.val.SymbolFactory;
import tardis.Options;

public class JBSEResult {
    private final String targetMethodClassName;
    private final String targetMethodDescriptor;
    private final String targetMethodName;
    private final State initialState;
    private final State preState;
    private final State finalState;
    private final boolean atJump;
    private final String targetBranch;
    private final HashMap<Long, String> stringLiterals;
    private final int depth;

    public JBSEResult(Options o, List<String> targetMethod) 
    throws InvalidClassFileFactoryClassException, InvalidInputException, IOException, 
    ClassFileNotFoundException, ClassFileIllFormedException, ClassFileNotAccessibleException, 
    IncompatibleClassFileException, PleaseLoadClassException, BadClassFileVersionException, 
    WrongClassNameException, CannotAssumeSymbolicObjectException, MethodNotFoundException, 
    MethodCodeNotFoundException, HeapMemoryExhaustedException {
        final State s = new State(true, HistoryPoint.startingPreInitial(true), 1_000, 100_000, o.getClasspath(), ClassFileFactoryJavassist.class, new HashMap<>(), new SymbolFactory());
        final ClassFile cf = s.getClassHierarchy().loadCreateClass(CLASSLOADER_APP, targetMethod.get(0), true);
        s.pushFrameSymbolic(cf, new Signature(targetMethod.get(0), targetMethod.get(1), targetMethod.get(2)));
        this.targetMethodClassName = targetMethod.get(0);
        this.targetMethodDescriptor = targetMethod.get(1);
        this.targetMethodName = targetMethod.get(2);
        this.initialState = s;
        this.preState = s.clone();
        this.finalState = s.clone();
        this.atJump = false;
        this.targetBranch = null;
        this.stringLiterals = new HashMap<>(Collections.emptyMap());
        this.depth = -1;
    }

    public JBSEResult(EvosuiteResult er, State initialState, State preState, State finalState, boolean atJump, String targetBranch, Map<Long, String> stringLiterals, int depth) {
        this.targetMethodClassName = er.getTargetMethodClassName();
        this.targetMethodDescriptor = er.getTargetMethodDescriptor();
        this.targetMethodName = er.getTargetMethodName();
        this.initialState = initialState.clone();
        this.preState = preState.clone();
        this.finalState = finalState.clone();
        this.atJump = atJump;
        this.targetBranch = (atJump ? targetBranch : null);
        this.stringLiterals = new HashMap<>(stringLiterals); //safety copy
        this.depth = depth;
    }

    public String getTargetClassName() {
        return this.targetMethodClassName;
    }

    public String getTargetMethodDescriptor() {
        return this.targetMethodDescriptor;
    }

    public String getTargetMethodName() {
        return this.targetMethodName;
    }

    public State getInitialState() {
        return this.initialState;
    }

    public State getPreState() {
        return this.preState;
    }

    public State getFinalState() {
        return this.finalState;
    }

    public boolean getAtJump() {
        return this.atJump;
    }

    public String getTargetBranch() {
        return this.targetBranch;
    }

    public Map<Long, String> getStringLiterals() {
        return this.stringLiterals;
    }

    public int getDepth() {
        return this.depth;
    }	
}
