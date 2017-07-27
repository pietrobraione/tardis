package exec;

import jbse.mem.State;

public class JBSEResult {
	private final State initialState;
	private final State finalState;
	private final int depth;
	
	public JBSEResult(State initialState, State finalState, int depth) {
		this.initialState = initialState.clone();
		this.finalState = finalState.clone();
		this.depth = depth;
	}
	
	public State getInitialState() {
		return this.initialState;
	}
	
	public State getFinalState() {
		return this.finalState;
	}
	
	public int getDepth() {
		return this.depth;
	}	
}
