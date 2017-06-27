package exec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.PathOptionHandler;

import sushi.configure.SignatureHandler;

public class Options {

	private static Options instance = null;

	public static Options I() {
		if (instance == null) {
			instance = new Options();
		}
		return instance;
	}
		
	@Option(name = "-test_method",
			usage = "Java signature of the test method for the guiding (concrete) execution",
			handler = SignatureHandler.class)
	private List<String> testMethodSignature;
	
	@Option(name = "-guided_method",
			usage = "Java signature of the guided method (to be executed symbolically)",
			handler = SignatureHandler.class)
	private List<String> guidedMethodSignature;
	
	@Option(name = "-max_depth",
			usage = "The maximum depth at which generation of tests is performed")
	private int maxdepth;
	
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
	
	public List<String> getTestMethod() {
		return this.testMethodSignature;
	}
	
	public void setTestMethod(String... signature) {
		if (signature.length != 3) {
			return;
		}
		this.testMethodSignature = Arrays.asList(signature);
	}
	
	public List<String> getGuidedMethod() {
		return this.guidedMethodSignature;
	}
	
	public void setGuidedMethod(String... signature) {
		if (signature.length != 3) {
			return;
		}
		this.guidedMethodSignature = Arrays.asList(signature);
	}
	
	public int getMaxDepth() {
		return this.maxdepth;
	}
	
	public void setMaxDepth(int maxdepth) {
		this.maxdepth = maxdepth;
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
}
