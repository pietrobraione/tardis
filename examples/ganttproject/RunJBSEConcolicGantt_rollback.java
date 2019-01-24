package ganttproject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import common.Settings;
import exec.Main;
import exec.Options;

public class RunJBSEConcolicGantt_rollback {
	public static void main(String[] s) throws IOException {
		final String className = "ganttproject/DependencyGraph";
		final String parametersSignature1 = "()V";
		final String methodName1 = "rollbackTransaction";
		
		final String testClass = "ganttproject/Test2";
		final String testMethodSignature = "()V";
		final String testMethod = "test0";
		final int maxDepth = 30;
		final int numOfThreads = 20;
	//	final int numOfThreads = 5;
		final long timeBudgetDuration = 120;
		final TimeUnit timeBudgetTimeUnit = TimeUnit.MINUTES;
		final Path[] classPath = {Settings.BIN_PATH, Settings.GANTTPROJECT_GUAVA, Settings.JBSE_PATH};
		
	//	ganttproject/DependencyGraph:(Lganttproject/Node;)Z:removeImplicitDependencies
	//	ganttproject/DependencyGraph:(Lganttproject/Node)Z:removeImplicitDependencies
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
		o.setHeapScope("ganttproject/Node", 3);
		o.setHeapScope("ganttproject/NodeData", 5);
		o.setHeapScope("ganttproject/GraphData", 2);
		o.setHeapScope("ganttproject/ExplicitDependencyImpl", 1);
		o.setHeapScope("ganttproject/ImplicitInheritedDependency", 1);
		o.setHeapScope("ganttproject/ImplicitSubSuperTaskDependency", 1);

		final Main m = new Main(o);
		m.start();
	}
}

