package treemap;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import sushi.configure.ParseException;
import jbse.meta.Analysis;
import common.Settings;
import exec.Main;
import exec.Options;

public class RunJBSEConcolic_containsValue {
	
	public static void main(String[] s) throws IOException, ParseException {
		final String className = "treemap/TreeMap";
		final String parametersSignature1 = "()I";
		final String methodName1 = "size";
		
		final String testClass = "treemap/Test9";
		final String testMethodSignature = "()V";
		final String testMethod = "test0";
		final int maxDepth = 300;
		final int numOfThreads = 20;
		final long timeBudgetDuration = 120;
		final TimeUnit timeBudgetTimeUnit = TimeUnit.MINUTES;
		final Path[] classPath = {Settings.BIN_PATH, Settings.JBSE_PATH, Settings.GUAVA, Settings.RHINO}; //, Settings.ARGS, Settings.JSON, Settings.JARJAR, 
	//	Settings.ANT_LAUNCHER, Settings.CAJA, Settings.ANT, Settings.COMPILER,
	//	Settings.PROTOBUF};
		
		final Options o = new Options();
		o.setTargetMethod(className, parametersSignature1, methodName1);
		o.setInitialTestCase(testClass, testMethodSignature, testMethod);
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
		
		o.setDepthScope(50);
		o.setCountScope(600);
		o.setHeapScope("treemap/TreeMap$Entry", 5); 				
		
		final Main m = new Main(o);
		m.start();
	}
}

