package tardis.implementation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jbse.mem.State;

public class JBSEResult {
    private final String targetClassName;
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
        this.depth = -1;
    }

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
        this.depth = -1;
    }

    public JBSEResult(EvosuiteResult er, State initialState, State preState, State finalState, boolean atJump, String targetBranch, Map<Long, String> stringLiterals, int depth) {
        this.targetClassName = null;
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
    
    public boolean isSeed() {
        return this.initialState == null;
    }
    
    public boolean hasTargetMethod() {
        return this.targetClassName == null; 
    }

    public String getTargetClassName() {
        return this.targetClassName;
    }

    public String getTargetMethodClassName() {
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
