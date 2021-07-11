package tardis;

import java.io.File;
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
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.MapOptionHandler;
import org.kohsuke.args4j.spi.PathOptionHandler;

import jbse.bc.Classpath;
import tardis.optionhandlers.LoggingLevelOptionHandler;
import tardis.optionhandlers.MultiPathOptionHandlerPatched;
import tardis.optionhandlers.MultiSignatureOptionHandler;
import tardis.optionhandlers.PercentageOptionHandler;
import tardis.optionhandlers.SignatureOptionHandler;

/**
 * The configuration options for TARDIS.
 * 
 * @author Pietro Braione
 */
public final class Options implements Cloneable {
    @Option(name = "-help",
            usage = "Prints usage and exits")
    private boolean help = false;
    
    @Option(name = "-verbosity",
            usage = "Verbosity of log messages: OFF, FATAL, ERROR, WARN, INFO, DEBUG, TRACE, ALL",
            handler = LoggingLevelOptionHandler.class)
    private Level verbosity = Level.INFO;

    @Option(name = "-options_config_path",
            usage = "Path for the classfile of the options configurator",
            handler = PathOptionHandler.class)
    private Path optionsConfiguratorPath = Paths.get(".");

    @Option(name = "-options_config_class",
            forbids = {"-target_class", "-target_method"},
            depends = {"-options_config_path"},
            usage = "Class name of the options configurator (default: none, either this or the -target_class or the -target_method option must be specified)")
    private String optionsConfiguratorClass;

    @Option(name = "-initial_test_random",
            forbids = {"-initial_test"},
            usage = "How the initial random tests must be generated; When set to METHOD, a random test is created for each visible method, when set to SUITE, EvoSuite is run to generate an initial test suite")
    private Randomness initialTestCaseRandomness = Randomness.METHOD;

    @Option(name = "-initial_test_path",
            usage = "Path where the source file of the initial test is found",
            handler = PathOptionHandler.class)
    private Path initialTestCasePath = Paths.get(".", "out");

    @Option(name = "-initial_test",
            forbids = {"-initial_test_random"},
            depends = {"-initial_test_path"},
            usage = "Java signature of the initial test case method for seeding concolic exploration",
            handler = SignatureOptionHandler.class)
    private List<String> initialTestCaseSignature = null;

    @Option(name = "-target_class",
            forbids = {"-options_config_class", "-target_method"},
            usage = "Name of the target class (containing the methods to test)")
    private String targetClassName;

    @Option(name = "-target_method",
            forbids = {"-options_config_class", "-target_class"},
            usage = "Java signature of the target method (the method to test)",
            handler = SignatureOptionHandler.class)
    private List<String> targetMethodSignature;

    @Option(name = "-visibility",
            usage = "For which methods defined in the target class should generate tests: PUBLIC (methods with public visibility), PACKAGE (methods with public, protected and package visibility)")
    private Visibility visibility = Visibility.PUBLIC;

    @Option(name = "-cov",
            usage = "Coverage: PATHS (all paths), BRANCHES (all branches), UNSAFE (failed assertion, works for only one assertion)")
    private Coverage coverage = Coverage.BRANCHES;
    
    @Option(name = "-max_depth",
            usage = "The maximum depth at which the target program is explored")
    private int maxDepth = 50;

    @Option(name = "-max_tc_depth",
            usage = "The maximum depth at which each single test case path is explored")
    private int maxTestCaseDepth = 25;

    @Option(name = "-max_count",
            usage = "The maximum state count after which, if the depth does not increase, the exploration of a path is abandoned")
    private long maxCount = 10_000_000;

    @Option(name = "-num_threads_jbse",
            usage = "The number of threads in the JBSE thread pool")
    private int numOfThreadsJBSE = 1;

    @Option(name = "-num_threads_evosuite",
            usage = "The number of threads in the EvoSuite thread pool")
    private int numOfThreadsEvosuite = 1;

    @Option(name = "-throttle_factor_jbse",
            usage = "The throttle factor for the JBSE thread pool",
            handler = PercentageOptionHandler.class)
    private float throttleFactorJBSE = 0.0f;

    @Option(name = "-throttle_factor_evosuite",
            usage = "The throttle factor for the EvoSuite thread pool",
            handler = PercentageOptionHandler.class)
    private float throttleFactorEvosuite = 0.0f;

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
    private String tmpDirName = tmpDirectoryNameDefault();

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

    @Option(name = "-java8_home",
            usage = "Home of a Java 8 JDK setup, necessary to EvoSuite",
            handler = PathOptionHandler.class)
    private Path java8Home;

    @Option(name = "-evosuite",
            usage = "Path to EvoSuite",
            handler = PathOptionHandler.class)
    private Path evosuitePath = Paths.get(".", "lib", "evosuite-shaded-1.0.6-SNAPSHOT.jar");

    @Option(name = "-sushi_lib",
            usage = "Path to SUSHI library",
            handler = PathOptionHandler.class)
    private Path sushiLibPath = Paths.get(".", "lib", "sushi-lib.jar");

    @Option(name = "-evosuite_time_budget_duration",
            usage = "Duration of the time budget for EvoSuite")
    private long evosuiteTimeBudgetDuration = 180;

    @Option(name = "-evosuite_time_budget_unit",
            usage = "Unit of the time budget for EvoSuite: NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS")
    private TimeUnit evosuiteTimeBudgetUnit = TimeUnit.SECONDS;

    @Option(name = "-evosuite_no_dependency",
            usage = "Whether the generated tests should have no dependency on the EvoSuite runtime")
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
    private int numMOSATargets = 5;

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

    @Option(name = "-max_simple_array_length",
            usage = "Maximum size of arrays with simple representation")
    private int maxSimpleArrayLength = 100_000;
    
    @Option(name = "-use_improvability_index",
            usage = "Whether to use the improvability index for path conditions selection in the JBSEResultInputOutputBuffer")
    private boolean useIndexImprovability = true;
    
    @Option(name = "-improvability_index_branch_pattern",
            usage = "A regular expression specifying the branches that must be considered for the calculation of the improvability index")
    private String indexImprovabilityBranchPattern = null;

    @Option(name = "-use_novelty_index",
            usage = "Whether to use the novelty index for path conditions selection in the JBSEResultInputOutputBuffer")
    private boolean useIndexNovelty = true;
    
    @Option(name = "-novelty_index_branch_pattern",
            usage = "A regular expression specifying the branches that must be considered for the calculation of the novelty index")
    private String indexNoveltyBranchPattern = null;

    @Option(name = "-use_infeasibility_index",
            usage = "Whether to use the infeasibility index for path conditions selection in the JBSEResultInputOutputBuffer")
    private boolean useIndexInfeasibility = true;
    
    @Option(name = "-infeasibility_index_threshold",
            usage = "The minimum size of the training set necessary for retraining.")
    private int indexInfeasibilityThreshold = 200;

    public boolean getHelp() {
        return this.help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public Level getVerbosity() {
        return this.verbosity;
    }

    public void setVerbosity(Level verbosity) {
        if (verbosity == null) {
            throw new IllegalArgumentException("Attempted to set verbosity to null.");
        }
        this.verbosity = verbosity;
    }
    
    public boolean hasOptionsConfigurator() {
    	return this.optionsConfiguratorClass != null;
    }
    
    public Path getOptionsConfiguratorPath() {
    	return this.optionsConfiguratorPath;
    }
    
    public void setOptionsConfiguratorPath(Path optionsConfiguratorPath) {
        if (optionsConfiguratorPath == null) {
            throw new IllegalArgumentException("Attempted to set options configurator path to null.");
        }
    	this.optionsConfiguratorPath = optionsConfiguratorPath;
    }

    public String getOptionsConfiguratorClass() {
    	return this.optionsConfiguratorClass;
    }
    
    public void setOptionsConfiguratorClass(String optionsConfiguratorClass) {
        if (optionsConfiguratorClass == null) {
            throw new IllegalArgumentException("Attempted to set options configurator class to null.");
        }
    	this.optionsConfiguratorClass = optionsConfiguratorClass;
    	this.targetClassName = null;
    	this.targetMethodSignature = null;
    }

    public Randomness getInitialTestCaseRandom() {
    	return this.initialTestCaseRandomness;
    }
    
    public void setInitialTestCaseRandom(Randomness initialTestCaseRandomness) {
        if (initialTestCaseRandomness == null) {
            throw new IllegalArgumentException("Attempted to set initial test case randomness to null.");
        }
    	this.initialTestCaseRandomness = initialTestCaseRandomness;
    	this.initialTestCaseSignature = null;
    }

    public Path getInitialTestCasePath() {
        return this.initialTestCasePath;
    }

    public void setInitialTestCasePath(Path initialTestCasePath) {
        this.initialTestCasePath = initialTestCasePath;
    }

    public List<String> getInitialTestCase() {
        return (this.initialTestCaseSignature == null ? null : Collections.unmodifiableList(this.initialTestCaseSignature));
    }

    public void setInitialTestCase(String... signature) {
        if (signature == null) {
            throw new IllegalArgumentException("Attempted to set initial test case signature to null.");
        }
        if (signature.length != 3) {
            throw new IllegalArgumentException("Attempted to set initial test case signature to unrecognized sequence of strings.");
        }
        this.initialTestCaseSignature = Arrays.asList(signature.clone());
        this.initialTestCaseRandomness = null;
    }

    public void setInitialTestCaseNone() {
        this.initialTestCaseSignature = null;
        this.initialTestCaseRandomness = Randomness.METHOD;
    }

    public String getTargetClass() {
        return this.targetClassName;
    }

    public void setTargetClass(String targetClassName) {
        if (targetClassName == null) {
            throw new NullPointerException("Attempted to set target class name to null.");
        }
        this.targetClassName = targetClassName;
        this.optionsConfiguratorClass = null;
        this.targetMethodSignature = null;
    }
    
    private static String toPattern(String s) {
        return s
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("[", "\\[")
        .replace("$", "\\$");
    }
    
    public List<String> getTargetMethod() {
        return (this.targetMethodSignature == null ? null : Collections.unmodifiableList(this.targetMethodSignature));
    }

    public void setTargetMethod(String... signature) {
        if (signature == null) {
            throw new IllegalArgumentException("Attempted to set target method signature to null.");
        }
        if (signature.length != 3) {
            throw new IllegalArgumentException("Attempted to set target method signature to unrecognized sequence of strings.");
        }
        this.targetMethodSignature = Arrays.asList(signature.clone());
        this.optionsConfiguratorClass = null;
        this.targetClassName = null;
    }

    public String patternBranchesTarget() {
        return (getTargetClass() == null) ? (toPattern(getTargetMethod().get(0)) + ":" + toPattern(getTargetMethod().get(1)) + ":" + toPattern(getTargetMethod().get(2)) + ":.*:.*") : (toPattern(getTargetClass()) + "(\\$.*)*:.*:.*:.*:.*");
    }
    
    public String patternBranchesUnsafe() {
        return "jbse/meta/Analysis:\\(Z\\)V:ass3rt:1:4"; //TODO find a more robust way
    }
    
    public Visibility getVisibility() {
        return this.visibility;
    }

    public void setVisibility(Visibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("Attempted to set visibility to null.");
        }
        this.visibility = visibility;
    }	

    public Coverage getCoverage() {
        return this.coverage;
    }

    public void setCoverage(Coverage coverage) {
        if (coverage == null) {
            throw new IllegalArgumentException("Attempted to set coverage to null.");
        }
        this.coverage = coverage;
    }

    public int getMaxDepth() {
        return this.maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("Attempted to set maximum program exploration depth to a value less than 1.");
        }
        this.maxDepth = maxDepth;
    }

    public int getMaxTestCaseDepth() {
        return this.maxTestCaseDepth;
    }

    public void setMaxTestCaseDepth(int maxTestCaseDepth) {
        if (maxTestCaseDepth < 1) {
            throw new IllegalArgumentException("Attempted to set maximum test case exploration depth to a value less than 1.");
        }
        this.maxTestCaseDepth = maxTestCaseDepth;
    }

    public long getMaxCount() {
        return this.maxCount;
    }

    public void setMaxCount(long maxCount) {
        if (maxCount < 1) {
            throw new IllegalArgumentException("Attempted to set maximum state count to a value less than 1.");
        }
        this.maxCount = maxCount;
    }

    public float getThrottleFactorJBSE() {
        return this.throttleFactorJBSE;
    }

    public void setThrottleFactorJBSE(float throttleFactorJBSE) {
        if (throttleFactorJBSE < 0.0f || throttleFactorJBSE > 1.0f) {
            throw new IllegalArgumentException("Attempted to set JBSE throttle factor out of range [0.0, 1.0].");
        }
        this.throttleFactorJBSE = throttleFactorJBSE;
    }

    public float getThrottleFactorEvosuite() {
        return this.throttleFactorEvosuite;
    }

    public void setThrottleFactorEvosuite(float throttleFactorEvosuite) {
        if (throttleFactorEvosuite < 0.0f || throttleFactorEvosuite > 1.0f) {
            throw new IllegalArgumentException("Attempted to set Evosuite throttle factor out of range [0.0, 1.0].");
        }
        this.throttleFactorEvosuite = throttleFactorEvosuite;
    }

    public int getNumOfThreadsJBSE() {
        return this.numOfThreadsJBSE;
    }

    public void setNumOfThreadsJBSE(int numOfThreads) {
        if (numOfThreads < 1) {
            throw new IllegalArgumentException("Attempted to set JBSE number of thread to a value less than 1.");
        }
        this.numOfThreadsJBSE = numOfThreads;
    }

    public int getNumOfThreadsEvosuite() {
        return this.numOfThreadsEvosuite;
    }

    public void setNumOfThreadsEvosuite(int numOfThreads) {
        if (numOfThreads < 1) {
            throw new IllegalArgumentException("Attempted to set Evosuite number of thread to a value less than 1.");
        }
        this.numOfThreadsEvosuite = numOfThreads;
    }

    public List<Path> getClassesPath() {
        return this.classesPath;
    }

    public void setClassesPath(Path... paths) {
        if (paths == null) {
            throw new IllegalArgumentException("Attempted to set classpath to null.");
        }
        this.classesPath = Arrays.asList(paths.clone());
    }

    public Path getTmpDirectoryBase() {
        return this.tmpDirBase;
    }

    public void setTmpDirectoryBase(Path tmpDirBase) {
        if (tmpDirBase == null) {
            throw new IllegalArgumentException("Attempted to set the temporary base work directory to null.");
        }
        this.tmpDirBase = tmpDirBase;
    }

    public String getTmpDirectoryName() {
        return this.tmpDirName;
    }

    public void setTmpDirectoryName(String tmpDirName) {
        if (tmpDirName == null) {
            throw new IllegalArgumentException("Attempted to set the temporary work directory name to null.");
        }
        this.tmpDirName = tmpDirName;
    }

    public void setTmpDirectoryNameNone() {
    	this.tmpDirName = null;
    }
    
    public void setTmpDirectoryNameDefault() {
    	this.tmpDirName = tmpDirectoryNameDefault();
    }
    
    private static String tmpDirectoryNameDefault() {
    	return new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
    }
    
    public Path getTmpDirectoryPath() {
        if (this.tmpDirName == null) {
            return this.tmpDirBase;
        } else {
            return this.tmpDirBase.resolve(this.tmpDirName);
        }
    }

    public Path getTmpBinDirectoryPath() {
        return getTmpDirectoryPath().resolve("bin");
    }

    public Path getTmpWrappersDirectoryPath() {
        return getTmpDirectoryPath().resolve("wrap");
    }

    public Path getTmpTestsDirectoryPath() {
        return getTmpDirectoryPath().resolve("test");
    }

    public Path getOutDirectory() {
        return this.outDir;
    }

    public void setOutDirectory(Path outDir) {
        if (outDir == null) {
            throw new IllegalArgumentException("Attempted to set the output directory to null.");
        }
        this.outDir = outDir;
    }

    public Path getZ3Path() {
        return this.z3Path;
    }

    public void setZ3Path(Path z3Path) {
        if (z3Path == null) {
            throw new IllegalArgumentException("Attempted to set the Z3 path to null.");
        }
        this.z3Path = z3Path;
    }

    public Path getJBSELibraryPath() {
        return this.jbsePath;
    }

    public void setJBSELibraryPath(Path jbsePath) {
        if (z3Path == null) {
            throw new IllegalArgumentException("Attempted to set the JBSE jar library path to null.");
        }
        this.jbsePath = jbsePath;
    }

    public Path getJava8Home() {
        return this.java8Home;
    }

    public void setJava8Home(Path java8Home) {
        if (java8Home == null) {
            throw new IllegalArgumentException("Attempted to set the Java 8 home path to null.");
        }
        this.java8Home = java8Home;
    }
    
    public void setJava8HomeNone() {
    	this.java8Home = null;
    }
    
    public String getJava8Command() {
        if (this.java8Home == null) {
            return "java";
        } else {
            return this.java8Home.resolve("bin/java").toAbsolutePath().toString();
        }
    }

    public Path getEvosuitePath() {
        return this.evosuitePath;
    }

    public void setEvosuitePath(Path evosuitePath) {
        if (evosuitePath == null) {
            throw new IllegalArgumentException("Attempted to set the Evosuite jar path to null.");
        }
        this.evosuitePath = evosuitePath;
    }

    public Path getSushiLibPath() {
        return this.sushiLibPath;
    }

    public void setSushiLibPath(Path sushiLibPath) {
        if (sushiLibPath == null) {
            throw new IllegalArgumentException("Attempted to set the SUSHI-lib jar path to null.");
        }
        this.sushiLibPath = sushiLibPath;
    }

    public Classpath getClasspath() throws IOException {
        final ArrayList<Path> extClasspath = 
            new ArrayList<>(Arrays.stream(System.getProperty("java.ext.dirs").split(File.pathSeparator))
            .map(s -> Paths.get(s)).collect(Collectors.toList()));
        final ArrayList<Path> userClasspath = new ArrayList<>();
        userClasspath.addAll(getClassesPath());
        return new Classpath(getJBSELibraryPath(), Paths.get(System.getProperty("java.home")), extClasspath, userClasspath);
    }

    public long getEvosuiteTimeBudgetDuration() {
        return this.evosuiteTimeBudgetDuration;
    }

    public void setEvosuiteTimeBudgetDuration(long evosuiteTimeBudgetDuration) {
        if (evosuiteTimeBudgetDuration < 0) {
            throw new IllegalArgumentException("Attempted to set the Evosuite time budget duration to a negative value.");
        }
        this.evosuiteTimeBudgetDuration = evosuiteTimeBudgetDuration;
    }

    public TimeUnit getEvosuiteTimeBudgetUnit() {
        return this.evosuiteTimeBudgetUnit;
    }

    public void setEvosuiteTimeBudgetUnit(TimeUnit evosuiteTimeBudgetUnit) {
        if (evosuiteTimeBudgetUnit == null) {
            throw new IllegalArgumentException("Attempted to set the Evosuite time budget time unit to null.");
        }
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
        if (globalTimeBudgetDuration < 0) {
            throw new IllegalArgumentException("Attempted to set the global time budget duration to a negative value.");
        }
        this.globalTimeBudgetDuration = globalTimeBudgetDuration;
    }

    public TimeUnit getGlobalTimeBudgetUnit() {
        return this.globalTimeBudgetUnit;
    }

    public void setGlobalTimeBudgetUnit(TimeUnit globalTimeBudgetUnit) {
        if (globalTimeBudgetUnit == null) {
            throw new IllegalArgumentException("Attempted to set the global time budget time unit to null.");
        }
        this.globalTimeBudgetUnit = globalTimeBudgetUnit;
    }

    public long getTimeoutMOSATaskCreationDuration() {
        return this.timeoutMOSATaskCreationDuration;
    }

    public void setTimeoutMOSATaskCreationDuration(long timeoutMOSATaskCreationDuration) {
        if (timeoutMOSATaskCreationDuration < 0) {
            throw new IllegalArgumentException("Attempted to set the MOSA timeout for task creation duration to a negative value.");
        }
        this.timeoutMOSATaskCreationDuration = timeoutMOSATaskCreationDuration;
    }

    public TimeUnit getTimeoutMOSATaskCreationUnit() {
        return this.timeoutMOSATaskCreationUnit;
    }

    public void setTimeoutMOSATaskCreationUnit(TimeUnit timeoutMOSATaskCreationUnit) {
        if (timeoutMOSATaskCreationUnit == null) {
            throw new IllegalArgumentException("Attempted to set the MOSA timeout for task creation time unit to null.");
        }
        this.timeoutMOSATaskCreationUnit = timeoutMOSATaskCreationUnit;
    }

    public int getNumMOSATargets() {
        return this.numMOSATargets;
    }

    public void setNumMOSATargets(int numMOSATargets) {
        if (numMOSATargets < 1) {
            throw new IllegalArgumentException("Attempted to set the number of MOSA targets to a value less than 1.");
        }
        this.numMOSATargets = numMOSATargets;
    }

    public Map<String, Integer> getHeapScope() {
        return (this.heapScope == null ? new HashMap<>() : new HashMap<>(this.heapScope));
    }

    public void setHeapScope(String className, int scope) {
        if (className == null) {
            throw new IllegalArgumentException("Attempted to set the heap scope for a class with null name.");
        }
        if (scope < 0) {
            throw new IllegalArgumentException("Attempted to set the heap scope for a class with negative value for the scope.");
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

    public int getCountScope() {
        return this.countScope;
    }

    public void setCountScope(int countScope) {
        if (countScope < 0) {
            throw new IllegalArgumentException("Attempted to set the count scope to a negative value.");
        }
        this.countScope = countScope;
    }

    public List<List<String>> getUninterpreted() {
        return this.uninterpreted;
    }

    public static List<String> sig(String className, String descriptor, String name) {
        return Arrays.asList(className, descriptor, name);
    }

    @SafeVarargs
    public final void setUninterpreted(List<String>... signatures) {
        if (signatures == null) {
            throw new IllegalArgumentException("Attempted to set the uninterpreted functions signatures to null.");
        }
        for (List<String> sig : signatures) {
            if (sig == null) {
                throw new IllegalArgumentException("Attempted to set a uninterpreted function signature to null.");
            }
            if (sig.size() != 3) {
                throw new IllegalArgumentException("Attempted to set a uninterpreted function signature to unrecognized sequence of strings.");
            }
        }
        this.uninterpreted = Arrays.asList(signatures.clone());
    }

    public int getMaxSimpleArrayLength() {
        return this.maxSimpleArrayLength;
    }
    
    public void setMaxSimpleArrayLength(int maxSimpleArrayLength) {
        if (maxSimpleArrayLength < 0) {
            throw new IllegalArgumentException("Attempted to set the maximum length of simple arrays to a negative value.");
        }
        this.maxSimpleArrayLength = maxSimpleArrayLength;
    }

    public boolean getUseIndexImprovability() {
        return this.useIndexImprovability;
    }
    
    public void setUseIndexImprovability(boolean useIndexImprovability) {
        this.useIndexImprovability = useIndexImprovability;
    }
    
    public String getIndexImprovabilityBranchPattern() {
    	return this.indexImprovabilityBranchPattern;
    }
    
    public void setIndexImprovabilityBranchPattern(String indexImprovabilityBranchPattern) {
        if (indexImprovabilityBranchPattern == null) {
            throw new IllegalArgumentException("Attempted to set the pattern of the branches for the improvability index to null.");
        }
    	this.indexImprovabilityBranchPattern = indexImprovabilityBranchPattern;
    }
    
    public boolean getUseIndexNovelty() {
        return this.useIndexNovelty;
    }
    
    public void setUseIndexNovelty(boolean useIndexNovelty) {
        this.useIndexNovelty = useIndexNovelty;
    }
    
    public String getIndexNoveltyBranchPattern() {
    	return this.indexNoveltyBranchPattern;
    }
    
    public void setIndexNoveltyBranchPattern(String indexNoveltyBranchPattern) {
        if (indexNoveltyBranchPattern == null) {
            throw new IllegalArgumentException("Attempted to set the pattern of the branches for the novelty index to null.");
        }
    	this.indexNoveltyBranchPattern = indexNoveltyBranchPattern;
    }
    
    public boolean getUseIndexInfeasibility() {
        return this.useIndexInfeasibility;
    }
    
    public void setUseIndexInfeasibility(boolean useInfeasibilityIndex) {
        this.useIndexInfeasibility = useInfeasibilityIndex;
    }
    
    public int getIndexInfeasibilityThreshold() {
        return this.indexInfeasibilityThreshold;
    }
    
    public void setIndexInfeasibilityThreshold(int indexInfeasibilityThreshold) {
        if (indexInfeasibilityThreshold < 1) {
            throw new IllegalArgumentException("Attempted to set the retraining threshold for the infeasibility index to a value less than 1.");
        }
        this.indexInfeasibilityThreshold = indexInfeasibilityThreshold;
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
