package tardis;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Settings {
	//Pietro's settings
	public static final Path TARDIS_WORKSPACE      = Paths.get("C:\\Users\\Flora\\Desktop\\TARDIS\\tardis");
	public static final Path EXPERIMENTS_WORKSPACE = Paths.get("C:\\Users\\Flora\\Desktop\\TARDIS\\tardis-experiments");
	public static final Path Z3_PATH               = Paths.get("C:\\Users\\Flora\\Desktop\\z3-master\\build\\z3");	
	
	//these are good for everyone
	public static final Path JBSE_PATH          = TARDIS_WORKSPACE.resolve(Paths.get("jbse", "build", "classes", "java", "main"));
	public static final Path EVOSUITE_PATH      = TARDIS_WORKSPACE.resolve(Paths.get("lib", "evosuite-shaded-1.0.3.jar"));
	public static final Path EVOSUITE_MOSA_PATH = TARDIS_WORKSPACE.resolve(Paths.get("lib", "evosuite-shaded-1.0.6-SNAPSHOT.jar"));
	public static final Path SUSHI_LIB_PATH     = TARDIS_WORKSPACE.resolve(Paths.get("runtime", "build", "classes", "java", "main"));
	public static final Path BIN_PATH           = EXPERIMENTS_WORKSPACE.resolve("bin");
	public static final Path OUT_PATH           = EXPERIMENTS_WORKSPACE.resolve("tardis-test");
	public static final Path TMP_BASE_PATH      = EXPERIMENTS_WORKSPACE.resolve("tardis-out");
	public static final Path EXAMPLES_PATH      = EXPERIMENTS_WORKSPACE.resolve("tardis-src");
}
