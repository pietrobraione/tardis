package tg;

import java.util.concurrent.TimeUnit;

import common.Settings;
import exec.Main;
import exec.Options;

public class RunTestgen {
	public static void main(String[] s) {
		final String className = "tg/Testgen";
		final String parametersSignature = "(Ltg/Testgen$Node;I)Ltg/Testgen$Node;";
		final String methodName  = "getNode";
		final String testClass = "tg/Test";
		final String testMethodSignature = "()V";
		final String testMethod = "test0";
		final int maxdepth = 50;
		final int numOfThreads = 5;
		final long timeBudgetDuration = 10;
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
		o.setSushiLibPath(Settings.SUSHI_LIB_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_MOSA_PATH);
		o.setUseMOSA(true);
		o.setNumMOSATargets(5);
		o.setGlobalTimeBudgetDuration(timeBudgetDuration);
		o.setGlobalTimeBudgetUnit(timeBudgetTimeUnit);
	
		final Main m = new Main(o);
		m.start();
	}
}
