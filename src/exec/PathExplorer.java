package exec;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import common.Settings;
import jbse.algo.exc.CannotManageStateException;
import jbse.bc.exc.InvalidClassFileFactoryClassException;
import jbse.common.exc.ClasspathException;
import jbse.dec.exc.DecisionException;
import jbse.jvm.exc.CannotBacktrackException;
import jbse.jvm.exc.CannotBuildEngineException;
import jbse.jvm.exc.EngineStuckException;
import jbse.jvm.exc.FailureException;
import jbse.jvm.exc.InitializationException;
import jbse.jvm.exc.NonexistingObservedVariablesException;
import jbse.mem.Clause;
import jbse.mem.State;
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.ThreadStackEmptyException;

public class PathExplorer {
	
	private RunnerPath rp;
	private PathConditionHandler handlerPC;
	
	static private String indent = "";
	static private int testcount = 0;
	
	public PathExplorer(RunnerPath runner){
		this.rp = runner;
		handlerPC = new PathConditionHandler();
		
	}

	
	public void explore(int pos, int depth, TestCase tc) 
			throws DecisionException, CannotBuildEngineException, InitializationException, 
					InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
					ClasspathException, CannotBacktrackException, CannotManageStateException, 
					ThreadStackEmptyException, ContradictionException, EngineStuckException, 
					FailureException {
		
		State alreadyDoneState = rp.runProgram(tc, -1).get(0);
		Collection<Clause> alreadyDonePC = alreadyDoneState.getPathCondition();
		int alreadyDoneDepth = alreadyDoneState.getDepth();
		
		System.out.println(indent + "Test_" + (++testcount) + ": Executed PC= " + alreadyDonePC); 
		
		for(int i = pos; i < alreadyDoneDepth - 1 && depth>0 ; i++){
			
			System.out.println(indent + "POS=" + i);
			
			 List<State> newStates = rp.runProgram(tc, i);
		
				Collection<Clause> currentPC;
				
				int j = 0;
				for (State newState : newStates) {
					
					currentPC = newState.getPathCondition();
			
					if(alreadyExplored(currentPC, alreadyDonePC)){
						continue;
					}
					
					System.out.println(indent + "** currently considered PC: " + currentPC);
					System.out.print(indent);
					String indentBak = indent;
					indent += "  ";
					handlerPC.generateTestCases(rp, j, i, newState);
					indent = indentBak;
					System.out.println(indent + "BACK"); 
					j++;

				}
				
		}
		
		System.out.println(indent + "DONE"); 
	}
	
	private boolean alreadyExplored(Collection<Clause> newPC, Collection<Clause> oldPC){
		Collection<Clause> donePC = Arrays.asList(Arrays.copyOfRange(
				oldPC.toArray(new Clause[0]), 0, 
				newPC.size()));
		if (donePC.toString().equals(newPC.toString())){
			return true;
		}else{
			return false;
		}
	}

	
	
	
	
	
}

