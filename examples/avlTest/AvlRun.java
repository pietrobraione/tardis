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
		final int evosuiteTimeBudget = 90;
		final long timeBudgetDuration = 30;
		final TimeUnit timeBudgetTimeUnit = TimeUnit.MINUTES;
		
		final Options o = new Options();
		o.setTargetMethod(className, parametersSignature, methodName);
		o.setInitialTestCase(testClass, testMethodSignature, testMethod);
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
		o.setUseMOSA(true);
		o.setEvosuiteBudget(evosuiteTimeBudget);
		o.setTimeBudgetDuration(timeBudgetDuration);
		o.setTimeBudgetTimeUnit(timeBudgetTimeUnit);
	
		final Main m = new Main(o);
		m.start();
	}
}