package exec;

import jbse.mem.State;

public class JBSEResult {
	private State initialState;
	private State finalState;
	private int depth;
	
	public JBSEResult(State initialState, State finalState, int depth){
		this.initialState = initialState.clone();
		this.finalState = finalState.clone();
		this.depth = depth;
	}
	
	public void setInitialState(State initialState){
		this.initialState = initialState.clone();
	}
	
	public State getInitialState(){
		return this.initialState;
	}
	
	public void setFinalState(State finalState){
		this.finalState = finalState.clone();
	}
	
	public State getFinalState(){
		return this.finalState;
	}
	public void setDepth(int depth){
		this.depth = depth;
	}
	
	public int getDepth(){
		return this.depth;
	}
	
	
}
