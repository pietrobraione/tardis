package avlTest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import common.Settings;
import exec.Main;
import exec.Options;

public class AvlRun {
	public static void main(String[] s) throws IOException {
		final String targetClass = "avlTest/AvlTree";
		final String targetMethodDescriptor = "(I)I";
		final String targetMethodName  = "find";
		final String initialTestClass = "avlTest/AvlTest";
		final String initialTestMethodDescriptor = "()V";
		final String initialTestMethodName = "testFind";
		final int maxDepth = 50;
		final int numOfThreads = 5;
		final long globalTimeBudgetDuration = 30;
		final TimeUnit globalTimeBudgetTimeUnit = TimeUnit.MINUTES;
		
		final Options o = new Options();
		o.setTargetClass(targetClass);
		//o.setTargetMethod(targetClass, targetMethodDescriptor, targetMethodName);
		o.setInitialTestCase(initialTestClass, initialTestMethodDescriptor, initialTestMethodName);
		o.setMaxDepth(maxDepth);
		o.setNumOfThreads(numOfThreads);
		o.setTmpDirectoryBase(Settings.TMP_BASE_PATH);
		o.setZ3Path(Settings.Z3_PATH);
		o.setJBSELibraryPath(Settings.JBSE_PATH);
		o.setClassesPath(Settings.BIN_PATH);
		o.setOutDirectory(Settings.OUT_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_MOSA_PATH);
		o.setSushiLibPath(Settings.SUSHI_LIB_PATH);
		o.setUseMOSA(true);
		o.setNumMOSATargets(5);
		o.setGlobalTimeBudgetDuration(globalTimeBudgetDuration);
		o.setGlobalTimeBudgetUnit(globalTimeBudgetTimeUnit);
	
		final Main m = new Main(o);
		m.start();
	}
}