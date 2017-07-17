package exec;

import java.util.concurrent.LinkedBlockingQueue;

import common.Settings;
import concurrent.EvosuiteResult;
import concurrent.JBSEResult;
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
		
		
		String className = "tg/Testgen";
		String parametersSignature = "(Ltg/Testgen$Node;I)Ltg/Testgen$Node;";
		String methodName  = "getNode";
		String testClass = "tg/Test";
		String testMethodSignature = "()V";
		String testMethod = "test0";
		
		int maxdepth = 50;
		int numOfThreads = 5;
		Options o = new Options();
		
		o.setTestMethod(testClass, testMethodSignature, testMethod);
		o.setGuidedMethod(className, parametersSignature, methodName);
		o.setMaxDepth(maxdepth);
		o.setNumOfThreads(numOfThreads);
		o.setTmpDirectoryBase(Settings.TMP_BASE_PATH);
		o.setZ3Path(Settings.Z3_PATH);
		o.setJBSELibraryPath(Settings.JBSE_PATH);
		o.setJREPath(Settings.JRE_PATH);
		o.setBinPath(Settings.BIN_PATH);
		o.setOutDirectory(Settings.OUT_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_PATH);
		o.setSushiLibPath(Settings.SUSHI_LIB_PATH);
	
		LinkedBlockingQueue<JBSEResult> pathConditionBuffer = new LinkedBlockingQueue<>();
		LinkedBlockingQueue<EvosuiteResult> testCaseBuffer = new LinkedBlockingQueue<>();
		TestCase tc = new TestCase(o);
		EvosuiteResult item = new EvosuiteResult(tc, 0);
		testCaseBuffer.add(item);
		PathExplorer performerJBSE = new PathExplorer(o, testCaseBuffer, pathConditionBuffer, o.getNumOfThreads());
		PathConditionHandler performerEvosuite = new PathConditionHandler(o, pathConditionBuffer, testCaseBuffer, o.getNumOfThreads());
		
		performerJBSE.execute();
		performerEvosuite.execute();
	}
	
}