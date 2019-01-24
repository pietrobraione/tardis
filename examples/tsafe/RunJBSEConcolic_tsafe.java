package tsafe;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import sushi.configure.ParseException;
import common.Settings;
import exec.Main;
import exec.Options;

public class RunJBSEConcolic_tsafe {
	
	public static void main(String[] s) throws IOException, ParseException {
		final String className = "tsafe/TsafeLauncher";
		final String parametersSignature1 = "(Ltsafe/TrajectorySynthesizer;Ltsafe/RouteTrack;Ltsafe/Route;)V";
		final String methodName1 = "checkTrajectorySynthesis";
		/*
		final String testClass = "tsafe/Test1";
		final String testMethodSignature = "()V";
		final String testMethod = "test0";
		*/
		final int maxDepth = 2000;
		final int numOfThreads = 15;
		final long timeBudgetDuration = 120;
		final TimeUnit timeBudgetTimeUnit = TimeUnit.MINUTES;
		final Path[] classPath = {Settings.BIN_PATH, Settings.JBSE_PATH};
		
		final Options o = new Options();
		o.setTargetMethod(className, parametersSignature1, methodName1);
		
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
		o.setJBSEFilesPath(Settings.JBSE_FILES);
			
//		o.setHeapScope("common/LinkedList$Entry", 3);		
		final Main m = new Main(o);
		m.start();
	}
}

