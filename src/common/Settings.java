package common;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Settings {
	public static final Path WORKSPACE      = Paths.get("E:/EclipseNeon/Workspace");
	public static final Path SRC_PATH       = Paths.get(WORKSPACE.toString(), "DynamicSym", "src");
	public static final Path BIN_PATH       = Paths.get(WORKSPACE.toString(), "DynamicSym", "bin");
	public static final Path JBSE_PATH      = Paths.get("E:/jbse/jbse-master", "bin");
	public static final Path TMP_BASE_PATH  = Paths.get(WORKSPACE.toString(), "DynamicSym", "tmp");
	public static final Path EVOSUITE_PATH  = Paths.get("C:/Users", "Maxximo", "git", "sushi", "lib", "evosuite-shaded-1.0.3.jar");
	public static final Path SUSHI_LIB_PATH = Paths.get("C:/Users", "Maxximo", "git", "sushi-lib", "bin");
	public static final Path Z3_PATH        = Paths.get("E:/", "z3-master", "build", "z3");
	public static final Path OUT_PATH       = Paths.get(WORKSPACE.toString(), "DynamicSym", "testCase");
}
