package exec;


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
import jbse.mem.exc.ContradictionException;
import jbse.mem.exc.ThreadStackEmptyException;


public class Main {
	
	
	public static void main(String[] args) 
				throws DecisionException, CannotBuildEngineException, InitializationException, 
				InvalidClassFileFactoryClassException, NonexistingObservedVariablesException, 
				ClasspathException, CannotBacktrackException, CannotManageStateException, 
				ThreadStackEmptyException, ContradictionException, EngineStuckException, 
				FailureException {
	
		String testPackage = "tg";
		String testClass = "Test";
		String testMethodSignature = "()V";
		String testMethod = "test0";
		int maxdepth = 50;
	
		TestCase tc = new TestCase(testPackage + "/" + testClass, testMethodSignature, testMethod);
	
		RunnerPath rp = new RunnerPath();
		
		PathExplorer pe = new PathExplorer(rp);

		pe.explore(tc, 0, maxdepth);
	}
	
}