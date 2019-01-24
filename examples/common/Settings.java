package common;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class Settings {
	//Pietro's settings
	public static final Path JBSE_PATH 
	= Paths.get("D:/appro/sushi/jbse/target/classes");
	public static final Path EVOSUITE_PATH 
	= Paths.get("D:/appro/jbse-concolic/lib/evosuite-shaded-1.0.3.jar");
	public static final Path EVOSUITE_MOSA_PATH
	= Paths.get("D:/appro/jbse-concolic/lib/evosuite-shaded-1.0.6-SNAPSHOT.jar");
	public static final Path SUSHI_LIB_PATH
	= Paths.get("D:/appro/sushi/runtime/target/classes");
	public static final Path Z3_PATH
	= Paths.get("C:/Users/luca1/Desktop/z3-4.7.1-x64-win/bin/z3.exe");
	public static final Path WORKSPACE 
	= Paths.get("D:/appro/jbse-concolic/");
	public static final Path LOGS_PATH_CONDITIONS_PATH=Paths.get("D:/appro/jbse-concolic/logs-path-conditions");
	


	public static final Path GANTTPROJECT_GUAVA = Paths
			.get("D:/appro/jbse-concolic/lib/ganttproject-guava.jar");
	public static final Path GUAVA = Paths
			.get("D:/appro/jbse-concolic/lib/guava.jar");
	public static final Path RHINO = Paths
			.get("D:/appro/jbse-concolic/lib/rhino.jar");
	public static final Path JBSE_FILES = Paths
			.get("D:/appro/jbse-concolic/settings/closure_compiler_partial.jbse");
	
	

	public static final Path ARGS =
			Paths.get("D:/appro/jbse-concolic/lib/args4j-2.32.jar");
	public static final Path JSON =
			Paths.get("D:/appro/jbse-concolic/lib/json.jar");
	public static final Path JARJAR =
			Paths.get("D:/appro/jbse-concolic/lib/jarjar.jar");
	public static final Path ANT_LAUNCHER =
			Paths.get("D:/appro/jbse-concolic/lib/ant-launcher.jar");
	public static final Path CAJA =
			Paths.get("D:/appro/jbse-concolic/lib/caja-r4314.jar");
	public static final Path ANT =
			Paths.get("D:/appro/jbse-concolic/lib/ant.jar");
	public static final Path COMPILER =
			Paths.get("D:/appro/jbse-concolic/lib/compiler.jar");
	public static final Path PROTOBUF =
			Paths.get("D:/appro/jbse-concolic/lib/protobuf-java.jar");
    public static final Path JAVAPARSER 
    =Paths.get("D:/appro/jbse-concolic/lib/javaparser-core-3.4.0.jar");
    public static final Path TOOLS 
    =Paths.get("D:/appro/jbse-concolic/lib/tools.jar");
	
	
	//these are good for everyone
	public static final Path SRC_PATH       = WORKSPACE.resolve("src");
	public static final Path BIN_PATH       = WORKSPACE.resolve("D:/appro/jbse-concolic/bin");
	public static final Path OUT_PATH       = WORKSPACE.resolve("testCase");
	public static final Path TMP_BASE_PATH  = WORKSPACE.resolve("tmp");
	public static final Path JRE_PATH = JBSE_PATH.resolve("D:/appro/sushi/jbse/data/jre/rt.jar");

}
