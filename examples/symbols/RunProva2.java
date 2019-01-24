package symbols;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import common.Settings;
import exec.Main;
import exec.Options;
import jbse.bc.Signature;
public class RunProva2 {
	public static void main(String[] s) throws IOException {
		final String targetClass = "symbols/Prova2";
		
		/*riga sotto è la vecchia versione
		final String targetMethodDescriptor = "(Ljava/util/LinkedList;D)Ljava/lang/String;";
		*/
		
		final String targetMethodDescriptor = "(DD)Z";
		final String targetMethodName  = "m";
		
		final String initialTestClass = "symbols/Test3";
		final String initialTestMethodDescriptor = "()V";
		final String initialTestMethodName = "test0";
		
		
		final int maxDepth = 50;
		final int numOfThreads = 5;
		final long timeBudgetDuration = 10;
		final TimeUnit timeBudgetTimeUnit = TimeUnit.MINUTES;
		
		
		final Options o = new Options();
		o.setTargetMethod(targetClass, targetMethodDescriptor, targetMethodName);
		o.setInitialTestCase(initialTestClass, initialTestMethodDescriptor, initialTestMethodName);
		o.setMaxDepth(maxDepth);
		o.setNumOfThreads(numOfThreads);
		o.setTmpDirectoryBase(Settings.TMP_BASE_PATH);
		o.setLogsPathConditionsDirectoryPath(Settings.LOGS_PATH_CONDITIONS_PATH);
		o.setZ3Path(Settings.Z3_PATH);
		o.setJBSELibraryPath(Settings.JBSE_PATH);
		o.setJREPath(Settings.JRE_PATH);
		o.setClassesPath(Settings.BIN_PATH);
		o.setOutDirectory(Settings.OUT_PATH);
		o.setSushiLibPath(Settings.SUSHI_LIB_PATH);
		o.setEvosuitePath(Settings.EVOSUITE_MOSA_PATH);
		o.setUseMOSA(true);
		o.setNumMOSATargets(5);
		o.setGlobalTimeBudgetDuration(timeBudgetDuration);
		o.setGlobalTimeBudgetUnit(timeBudgetTimeUnit);
	
		
		//per approssimazioni
		/*o.setUninterpreted(new Signature("java/lang/String", "(Ljava/lang/Object;)Z", "equals"), "EQUALS");
		o.setUninterpreted(new Signature("java/lang/Object", "(Ljava/lang/Object;)Z", "equals"), "EQUALS");
		o.setUninterpreted(new Signature("java/lang/String", "()Ljava/lang/String;", "toString"), "TO_STRING");
		o.setUninterpreted(new Signature("java/lang/Object", "()Ljava/lang/String;", "toString"), "TO_STRING");
		o.setUninterpreted(new Signature("java/lang/Integer", "()Ljava/lang/String;", "toString"), "TO_STRING");
		*/
		final Main m = new Main(o);
		m.start();
	}
}


