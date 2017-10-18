package exec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.PathOptionHandler;

import sushi.configure.SignatureHandler;

public class Options implements Cloneable {
	@Option(name = "-help",
			usage = "Prints usage and exits")
	private boolean help = false;

	@Option(name = "-initial_test",
			usage = "Java signature of the initial test case method for seeding concolic exploration",
			handler = SignatureHandler.class)
	private List<String> initialTestCaseSignature;
	
	@Option(name = "-target_method",
			usage = "Java signature of the target method (the method to test)",
			handler = SignatureHandler.class)
	private List<String> targetMethodSignature;
	
	@Option(name = "-max_depth",
			usage = "The maximum depth at which generation of tests is performed")
	private int maxDepth;
	
	@Option(name = "-num_threads",
			usage = "The number of threads in the thread pool")
	private int numOfThreads;
	
	@Option(name = "-bin",
			usage = "The bin directory of the project to analyze")
	private Path binPath = Paths.get(".", "bin");
	
	@Option(name = "-tmp_base",
			usage = "Base directory where the temporary subdirectory is found or created",
			handler = PathOptionHandler.class)
	private Path tmpDirBase = Paths.get(".", "tmp");
	
	@Option(name = "-tmp_name",
			usage = "Name of the temporary subdirectory to use or create")
	private String tmpDirName = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
	
	@Option(name = "-out",
			usage = "Output directory where the java source files of the created test suite must be put",
			handler = PathOptionHandler.class)
	private Path outDir = Paths.get(".", "out");
	
	@Option(name = "-z3",
			usage = "Path to Z3 binary (default: none, expect Z3 on the system PATH)",
			handler = PathOptionHandler.class)
	private Path z3Path;
	
	@Option(name = "-java8_home",
			usage = "Path to Java 8 home (default: none, expect Java executables on the system PATH)",
			handler = PathOptionHandler.class)
	private Path java8Path;
	
	@Option(name = "-jbse_lib",
			usage = "Path to JBSE library",
			handler = PathOptionHandler.class)
	private Path jbsePath = Paths.get(".", "lib", "jbse.jar");

	@Option(name = "-jbse_jre",
			usage = "Path to JRE library suitable for JBSE analysis",
			handler = PathOptionHandler.class)
	private Path jrePath = Paths.get(".", "data", "jre", "rt.jar");
	
	@Option(name = "-evosuite",
			usage = "Path to Evosuite",
			handler = PathOptionHandler.class)
	private Path evosuitePath = Paths.get(".", "lib", "evosuite.jar");
	
	@Option(name = "-sushi_lib",
			usage = "Path to Sushi library",
			handler = PathOptionHandler.class)
	private Path sushiPath = Paths.get(".", "lib", "sushi-lib.jar");
	
	@Option(name = "-evosuite_time_budget",
			usage = "Time budget in seconds for evosuite")
	private int timeBudgetEvosuite = 180;
	
	@Option(name = "-time_budget_duration",
			usage = "Duration of the time budget")
	private long timeBudgetDuration = 10;
	
	@Option(name = "-time_budget_unit",
			usage = "Unit of the time budget: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS")
	private TimeUnit timeBudgetTimeUnit = TimeUnit.MINUTES;
	
	@Option(name = "-timeout_mosa_task_creation_duration",
			usage = "Duration of the timeout after which a MOSA job is created")
	private long timeoutMOSATaskCreationDuration = 30;
	
	@Option(name = "-timeout_mosa_task_creation_unit",
			usage = "Unit of the timeout after which a MOSA job is created: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS")
	private TimeUnit timeoutMOSATaskCreationUnit = TimeUnit.SECONDS;

	@Option(name = "-num_mosa_targets",
			usage = "Maximum number of target passed to a MOSA job")
	private int numMOSATargets = 1;
	
	@Option(name = "-use_mosa",
			usage = "Set to true if you want to use MOSA, false for ordinary EvoSuite")
	private boolean useMOSA = false;

	public boolean getHelp() {
		return this.help;
	}
	
	public void setHelp(boolean help) {
		this.help = help;
	}
		
	public List<String> getInitialTestCase() {
		return Collections.unmodifiableList(this.initialTestCaseSignature);
	}
	
	public void setInitialTestCase(String... signature) {
		if (signature.length != 3) {
			return;
		}
		this.initialTestCaseSignature = Arrays.asList(signature.clone());
	}
	
	public List<String> getTargetMethod() {
		return Collections.unmodifiableList(this.targetMethodSignature);
	}
	
	public void setTargetMethod(String... signature) {
		if (signature.length != 3) {
			return;
		}
		this.targetMethodSignature = Arrays.asList(signature.clone());
	}
	
	public int getMaxDepth() {
		return this.maxDepth;
	}
	
	public void setMaxDepth(int maxdepth) {
		this.maxDepth = maxdepth;
	}
	
	public int getNumOfThreads() {
		return this.numOfThreads;
	}
	
	public void setNumOfThreads(int numOfThreads) {
		this.numOfThreads = numOfThreads;
	}
	
	public Path getBinPath() {
		return this.binPath;
	}
	
	public void setBinPath(Path path) {
		this.binPath = path;
	}
	
	public Path getTmpDirectoryBase() {
		return this.tmpDirBase;
	}
	
	public void setTmpDirectoryBase(Path base) {
		this.tmpDirBase = base;
	}
	
	public String getTmpDirectoryName() {
		return this.tmpDirName;
	}
	
	public void setTmpDirectoryName(String name) {
		this.tmpDirName = name;
	}
	
	public Path getOutDirectory() {
		return this.outDir;
	}
	
	public void setOutDirectory(Path dir) {
		this.outDir = dir;
	}
	
	public Path getZ3Path() {
		return this.z3Path;
	}
	
	public void setZ3Path(Path z3Path) {
		this.z3Path = z3Path;
	}
	
	public Path getJava8Path() {
		return this.java8Path;
	}
	
	public void setJava8Path(Path java8Path) {
		this.java8Path = java8Path;
	}
	
	public Path getJBSELibraryPath() {
		return this.jbsePath;
	}

	public void setJBSELibraryPath(Path jbsePath) {
		this.jbsePath = jbsePath;
	}
	
	public Path getJREPath() {
		return this.jrePath;
	}

	public void setJREPath(Path jrePath) {
		this.jrePath = jrePath;
	}
	
	public Path getEvosuitePath() {
		return this.evosuitePath;
	}
	
	public void setEvosuitePath(Path evosuitePath) {
		this.evosuitePath = evosuitePath;
	}
	
	public Path getSushiLibPath() {
		return this.sushiPath;
	}
	
	public void setSushiLibPath(Path sushiPath) {
		this.sushiPath = sushiPath;
	}
	
	public int getEvosuiteBudget() {
		return this.timeBudgetEvosuite;
	}
	
	public void setEvosuiteBudget(int budgetEvosuite) {
		this.timeBudgetEvosuite = budgetEvosuite;
	}
	
	public long getTimeBudgetDuration() {
		return this.timeBudgetDuration;
	}
	
	public void setTimeBudgetDuration(long timeBudgetDuration) {
		this.timeBudgetDuration = timeBudgetDuration;
	}
	
	public TimeUnit getTimeBudgetTimeUnit() {
		return this.timeBudgetTimeUnit;
	}
	
	public void setTimeBudgetTimeUnit(TimeUnit timeBudgetTimeUnit) {
		this.timeBudgetTimeUnit = timeBudgetTimeUnit;
	}
	
	public long getTimeoutMOSATaskCreationDuration() {
		return this.timeoutMOSATaskCreationDuration;
	}
	
	public void setTimeoutMOSATaskCreationDuration(long timeoutMOSATaskCreationDuration) {
		this.timeoutMOSATaskCreationDuration = timeoutMOSATaskCreationDuration;
	}
	
	public TimeUnit getTimeoutMOSATaskCreationUnit() {
		return this.timeoutMOSATaskCreationUnit;
	}
	
	public void setTimeoutMOSATaskCreationUnit(TimeUnit timeoutMOSATaskCreationUnit) {
		this.timeoutMOSATaskCreationUnit = timeoutMOSATaskCreationUnit;
	}
	
	public int getNumMOSATargets() {
		return this.numMOSATargets;
	}
	
	public void setNumMOSATargets(int numMOSATargets) {
		this.numMOSATargets = numMOSATargets;
	}
	
	public boolean getUseMOSA() {
		return this.useMOSA;
	}
	
	public void setUseMOSA(boolean useMOSA) {
		this.useMOSA = useMOSA;
	}
	
	@Override
	public Options clone() {
		try {
			//all objects referred by fields of an Options object 
			//are not mutable, so shallow copy is OK
			return (Options) super.clone();
		} catch (CloneNotSupportedException e) {
			//this should never happen
			throw new AssertionError("super.clone() raised CloneNotSupportedException");
		}
	}
}
