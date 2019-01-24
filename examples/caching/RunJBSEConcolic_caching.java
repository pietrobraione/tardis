package caching;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import sushi.configure.ParseException;
import common.Settings;
import exec.Main;
import exec.Options;

public class RunJBSEConcolic_caching {
	
	public static void main(String[] s) throws IOException, ParseException {
		final String className = "caching/NodeCachingLinkedList";
	
		
		final String parametersSignature1 = "(Ljava/lang/Object;)Z";
		final String methodName1 = "add";
		/*
		final String testClass = "caching/Test1";
		final String testMethodSignature = "()V";
		final String testMethod = "test0";
		*/
		final int maxDepth = 30;
		final int numOfThreads = 20;
		final long timeBudgetDuration = 120;
		final TimeUnit timeBudgetTimeUnit = TimeUnit.MINUTES;
		final Path[] classPath = {Settings.BIN_PATH, Settings.JBSE_PATH};
		
		final Options o = new Options();
		//o.setTargetMethod(className, parametersSignature1, methodName1);
		o.setTargetClass(className);
		//o.setInitialTestCase(testClass, testMethodSignature, testMethod);
		o.setMaxDepth(maxDepth);
		o.setNumOfThreads(numOfThreads);
		o.setTmpDirectoryBase(Settings.TMP_BASE_PATH);
		o.setLogsPathConditionsDirectoryPath(Settings.LOGS_PATH_CONDITIONS_PATH);
		o.setZ3Path(Settings.Z3_PATH);
		o.setJBSELibraryPath(Settings.JBSE_PATH);
		o.setJREPath(Settings.JRE_PATH);
		o.setClassesPath(classPath);
		o.setOutDirectory(Settings.OUT_PATH);
		o.setSushiLibPath(Settings.SUSHI_LIB_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_MOSA_PATH);
		o.setUseMOSA(true);
		o.setNumMOSATargets(5);
		o.setGlobalTimeBudgetDuration(timeBudgetDuration);
		o.setGlobalTimeBudgetUnit(timeBudgetTimeUnit);
		
		o.setDepthScope(50);
		o.setCountScope(600);
		o.setHeapScope("node_caching_linked_list/NodeCachingLinkedList$LinkedListNode", 3); 		
			
		final Main m = new Main(o);
		m.start();
	}
}

