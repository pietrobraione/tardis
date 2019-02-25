package tardis;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.MapOptionHandler;
import org.kohsuke.args4j.spi.PathOptionHandler;

import jbse.bc.Classpath;
import sushi.configure.SignatureHandler;
import sushi.configure.Visibility;
import sushi.configure.MultiPathOptionHandlerPatched;
import tardis.implementation.MultiSignatureOptionHandler;

public final class Options implements Cloneable {
	@Option(name = "-help",
	        usage = "Prints usage and exits")
	private boolean help = false;

	@Option(name = "-initial_test",
			usage = "Java signature of the initial test case method for seeding concolic exploration",
			handler = SignatureHandler.class)
	private List<String> initialTestCaseSignature;
	
	@Option(name = "-initial_test_path",
			usage = "Path where the source file of the initial test is found",
			handler = PathOptionHandler.class)
	private Path initialTestCasePath = Paths.get(".", "out");
	
	@Option(name = "-target_class",
			usage = "Name of the target class (containing the methods to test)")
	private String targetClassName;
	
	@Option(name = "-visibility",
			usage = "For which methods defined in the target class should generate tests: PUBLIC (methods with public visibility), PACKAGE (methods with public, protected and package visibility)")
	private Visibility visibility = Visibility.PUBLIC;
	
	@Option(name = "-target_method",
			usage = "Java signature of the target method (the method to test)",
			handler = SignatureHandler.class)
	private List<String> targetMethodSignature;
	
	@Option(name = "-max_depth",
			usage = "The maximum depth at which generation of tests is performed")
	private int maxDepth = 50;
	
	@Option(name = "-num_threads",
			usage = "The number of threads in the thread pool")
	private int numOfThreads;
	
	@Option(name = "-classes",
			usage = "The classpath of the project to analyze",
			handler = MultiPathOptionHandlerPatched.class)
	private List<Path> classesPath;
	
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
			usage = "Path to Z3 binary",
			handler = PathOptionHandler.class)
	private Path z3Path = Paths.get("/usr", "bin", "z3");
	
	@Option(name = "-jbse_lib",
			usage = "Path to JBSE library",
			handler = PathOptionHandler.class)
	private Path jbsePath = Paths.get(".", "lib", "jbse.jar");

	@Option(name = "-evosuite",
			usage = "Path to Evosuite or MOSA",
			handler = PathOptionHandler.class)
	private Path evosuitePath = Paths.get(".", "lib", "evosuite.jar");
	
	@Option(name = "-sushi_lib",
			usage = "Path to Sushi library",
			handler = PathOptionHandler.class)
	private Path sushiPath = Paths.get(".", "lib", "sushi-lib.jar");
	
	@Option(name = "-evosuite_time_budget_duration",
			usage = "Duration of the time budget for EvoSuite")
	private int evosuiteTimeBudgetDuration = 180;
	
	@Option(name = "-evosuite_time_budget_unit",
	        usage = "Unit of the time budget for EvoSuite: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS")
	private TimeUnit evosuiteTimeBudgetUnit = TimeUnit.SECONDS;

	@Option(name = "-evosuite_no_dependency",
	        usage = "Whether the generated tests should have no dependency on the Evosuite runtime")
	private boolean evosuiteNoDependency = false;

	@Option(name = "-global_time_budget_duration",
			usage = "Duration of the global time budget")
	private long globalTimeBudgetDuration = 10;
	
	@Option(name = "-global_time_budget_unit",
			usage = "Unit of the global time budget: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS")
	private TimeUnit globalTimeBudgetUnit = TimeUnit.MINUTES;
	
	@Option(name = "-timeout_mosa_task_creation_duration",
			usage = "Duration of the timeout after which a MOSA job is created")
	private long timeoutMOSATaskCreationDuration = 5;
	
	@Option(name = "-timeout_mosa_task_creation_unit",
			usage = "Unit of the timeout after which a MOSA job is created: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS")
	private TimeUnit timeoutMOSATaskCreationUnit = TimeUnit.SECONDS;

	@Option(name = "-num_mosa_targets",
			usage = "Maximum number of target passed to a MOSA job")
	private int numMOSATargets = 1;
	
	@Option(name = "-use_mosa",
			usage = "Set to true if you want to use MOSA, false for ordinary EvoSuite")
	private boolean useMOSA = false;
	
	@Option(name = "-heap_scope",
			usage = "JBSE heap scope in the form <className1>=<maxNumInstances1>; multiple heap scopes can be specified",
			handler = MapOptionHandler.class)
	private Map<String, Integer> heapScope;
	
	@Option(name = "-count_scope",
			usage = "JBSE count scope, 0 means unlimited")
	private int countScope = 0;

	@Option(name = "-uninterpreted",
			usage = "List of signatures of uninterpreted methods",
			handler = MultiSignatureOptionHandler.class)
	private List<List<String>> uninterpreted = new ArrayList<>();
	
	public boolean getHelp() {
		return this.help;
	}
	
	public void setHelp(boolean help) {
		this.help = help;
	}
		
	public List<String> getInitialTestCase() {
		return (this.initialTestCaseSignature == null ? null : Collections.unmodifiableList(this.initialTestCaseSignature));
	}
	
	public void setInitialTestCase(String... signature) {
		if (signature.length != 3) {
			return;
		}
		this.initialTestCaseSignature = Arrays.asList(signature.clone());
	}
	
	public void setInitialTestCaseNone() {
		this.initialTestCaseSignature = null;
	}
	
	public Path getInitialTestCasePath() {
		return this.initialTestCasePath;
	}
	
	public void setInitialTestCasePath(Path initialTestCasePath) {
		this.initialTestCasePath = initialTestCasePath;
	}
	
	public String getTargetClass() {
		return this.targetClassName;
	}
	
	public void setTargetClass(String targetClassName) {
		this.targetClassName = targetClassName;
		this.targetMethodSignature = null;
	}

	public Visibility getVisibility() {
		return this.visibility;
	}
	
	public void setVisibility(Visibility visibility) {
		this.visibility = visibility;
	}	

	public List<String> getTargetMethod() {
		return (this.targetMethodSignature == null ? null : Collections.unmodifiableList(this.targetMethodSignature));
	}
	
	public void setTargetMethod(String... signature) {
		if (signature.length != 3) {
			return;
		}
		this.targetMethodSignature = Arrays.asList(signature.clone());
		this.targetClassName = null;
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
	
	public List<Path> getClassesPath() {
		return this.classesPath;
	}
	
	public void setClassesPath(Path... paths) {
		this.classesPath = Arrays.asList(paths);
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
	
	public Path getTmpDirectoryPath() {
		if (this.tmpDirName == null) {
			return this.tmpDirBase;
		} else {
			return this.tmpDirBase.resolve(this.tmpDirName);
		}
	}
	
	public Path getTmpBinTestsDirectoryPath() {
		return getTmpDirectoryPath().resolve("bin");
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
	
	public Path getJBSELibraryPath() {
		return this.jbsePath;
	}

	public void setJBSELibraryPath(Path jbsePath) {
		this.jbsePath = jbsePath;
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
	
	public Classpath getClasspath() throws IOException {
		final ArrayList<Path> userClasspath = new ArrayList<>();
		userClasspath.add(getJBSELibraryPath());
		userClasspath.addAll(getClassesPath());
		return new Classpath(Paths.get(System.getProperty("java.home")), Collections.emptyList(), userClasspath);
	}
	
	public int getEvosuiteTimeBudgetDuration() {
		return this.evosuiteTimeBudgetDuration;
	}
	
	public void setEvosuiteTimeBudgetDuration(int evosuiteTimeBudgetDuration) {
		this.evosuiteTimeBudgetDuration = evosuiteTimeBudgetDuration;
	}
	
	public TimeUnit getEvosuiteTimeBudgetUnit() {
		return this.evosuiteTimeBudgetUnit;
	}
	
	public void setEvosuiteTimeBudgetUnit(TimeUnit evosuiteTimeBudgetUnit) {
		this.evosuiteTimeBudgetUnit = evosuiteTimeBudgetUnit;
	}
	
	public boolean getEvosuiteNoDependency() {
	    return this.evosuiteNoDependency;
	}
	
        public void setEvosuiteNoDependency(boolean evosuiteNoDependency) {
            this.evosuiteNoDependency = evosuiteNoDependency;
        }
        
	public long getGlobalTimeBudgetDuration() {
		return this.globalTimeBudgetDuration;
	}
	
	public void setGlobalTimeBudgetDuration(long globalTimeBudgetDuration) {
		this.globalTimeBudgetDuration = globalTimeBudgetDuration;
	}
	
	public TimeUnit getGlobalTimeBudgetUnit() {
		return this.globalTimeBudgetUnit;
	}
	
	public void setGlobalTimeBudgetUnit(TimeUnit globalTimeBudgetUnit) {
		this.globalTimeBudgetUnit = globalTimeBudgetUnit;
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
	
	public void setHeapScope(String className, int scope) {
		if (className == null) {
			return;
		}
		if (this.heapScope == null) {
			this.heapScope = new HashMap<>();
		}
		this.heapScope.put(className, Integer.valueOf(scope));
	}
	
	public void setHeapScopeUnlimited(String className) {
		if (this.heapScope == null) {
			return;
		}
		this.heapScope.remove(className);
	}
	
	public void setHeapScopeUnlimited() {
		this.heapScope = new HashMap<>();
	}
	
	public Map<String, Integer> getHeapScope() {
		return (this.heapScope == null ? null : Collections.unmodifiableMap(this.heapScope));
	}
	
	public void setCountScope(int countScope) {
		this.countScope = countScope;
	}
	
	public int getCountScope() {
		return this.countScope;
	}
	
	public List<List<String>> getUninterpreted() {
		return this.uninterpreted;
	}
	
	public static List<String> sig(String className, String descriptor, String name) {
		return Arrays.asList(className, descriptor, name);
	}
	
	@SafeVarargs
	public final void setUninterpreted(List<String>... signatures) {
		this.uninterpreted = Arrays.asList(signatures);
	}
	
	@Override
	public Options clone() {
		try {
			final Options theClone = (Options) super.clone();
			if (this.heapScope != null) {
				theClone.heapScope = new HashMap<>(this.heapScope);
			}
			return theClone;
		} catch (CloneNotSupportedException e) {
			//this should never happen
			throw new AssertionError("super.clone() raised CloneNotSupportedException");
		}
	}
}
