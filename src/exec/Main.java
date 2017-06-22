package exec;

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
		String className = "Testgen";
		String parametersSignature = "(Ltg/Testgen$Node;I)Ltg/Testgen$Node;";
		String methodName  = "getNode";
		String testClass = "Test";
		String testMethodSignature = "()V";
		String testMethod = "test0";
		
		int maxdepth = 50;
	
		Options o = new Options();
		
		o.setTestMethod(testPackage, testClass, testMethodSignature, testMethod);
		o.setGuidedMethod(testPackage, className, parametersSignature, methodName);
		o.setMaxDepth(maxdepth);
		o.setTmpDirectoryBase(Settings.TMP_BASE_PATH);
		o.setZ3Path(Settings.Z3_PATH);
		o.setJBSELibraryPath(Settings.JBSE_PATH);
		o.setJREPath(Settings.JRE_PATH);
		o.setBinPath(Settings.BIN_PATH);
		o.setOutDirectory(Settings.OUT_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_PATH);
		o.setSushiLibPath(Settings.SUSHI_LIB_PATH);
		
		TestCase tc = new TestCase(o);
		
		RunnerPath rp = new RunnerPath(o);
		
		PathExplorer pe = new PathExplorer(o, rp);

		pe.explore(tc, 0, o.getMaxDepth());
	}
	
}