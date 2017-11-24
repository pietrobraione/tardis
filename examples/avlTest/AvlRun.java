package avlTest;

import java.util.concurrent.TimeUnit;

import common.Settings;
import exec.Main;
import exec.Options;

public class AvlRun {
	public static void main(String[] s) {
		final String className = "avlTest/AvlTree";
		final String parametersSignature = "(I)I";
		final String methodName  = "find";
		final String testClass = "avlTest/AvlTest";
		final String testMethodSignature = "()V";
		final String testMethod = "testFind";
		final int maxdepth = 50;
		final int numOfThreads = 5;
		final long globalTimeBudgetDuration = 30;
		final TimeUnit globalTimeBudgetTimeUnit = TimeUnit.MINUTES;
		
		final Options o = new Options();
		o.setTargetClass(className);
		//o.setTargetMethod(className, parametersSignature, methodName);
		o.setInitialTestCase(testClass, testMethodSignature, testMethod);
		o.setMaxDepth(maxdepth);
		o.setNumOfThreads(numOfThreads);
		o.setTmpDirectoryBase(Settings.TMP_BASE_PATH);
		o.setZ3Path(Settings.Z3_PATH);
		o.setJBSELibraryPath(Settings.JBSE_PATH);
		o.setJREPath(Settings.JRE_PATH);
		o.setBinPath(Settings.BIN_PATH);
		o.setOutDirectory(Settings.OUT_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_MOSA_PATH);
		o.setSushiLibPath(Settings.SUSHI_LIB_PATH);
		o.setUseMOSA(true);
		o.setNumMOSATargets(/*1 */ 5 /*10 20 50 */);
		o.setGlobalTimeBudgetDuration(globalTimeBudgetDuration);
		o.setGlobalTimeBudgetUnit(globalTimeBudgetTimeUnit);
	
		final Main m = new Main(o);
		m.start();
	}
}