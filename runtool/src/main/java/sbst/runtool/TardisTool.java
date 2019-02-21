package sbst.runtool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class TardisTool implements ITestingTool {
    private final String TARDIS;
    private final String Z3_BIN;
    private final String TMP_DIR;
    private final String OUT_DIR;
    private final String SUSHI_LIB;
    private final String JBSE_LIB;
    private final String EVOSUITE_LIB;
    private final String ARGS4J_LIB;
    private final String TOOLS_LIB;

    private final String classPathTardis;
    private String classPathSUT;

    public TardisTool(String currentDirectory) {
        TARDIS         = currentDirectory + "/lib/tardis-master-0.1.0-SNAPSHOT.jar";
        Z3_BIN         = currentDirectory + "/opt/bin/z3";
        TMP_DIR        = currentDirectory + "/temp/data";
        OUT_DIR        = currentDirectory + "/temp/testcases";
        SUSHI_LIB      = currentDirectory + "/lib/sushi-lib-0.2.0-SNAPSHOT.jar";
        JBSE_LIB       = currentDirectory + "/lib/jbse-0.9.0-SNAPSHOT-shaded.jar";
        EVOSUITE_LIB   = currentDirectory + "/lib/evosuite-shaded-1.0.6-SNAPSHOT.jar";
        ARGS4J_LIB     = currentDirectory + "/lib/args4j-2.32.jar";
        TOOLS_LIB      = System.getProperty("java.home") + "/lib/tools.jar";
        final StringBuilder sb = new StringBuilder();
        sb.append(TARDIS); sb.append(":");
        sb.append(SUSHI_LIB); sb.append(":");
        sb.append(JBSE_LIB); sb.append(":");
        sb.append(EVOSUITE_LIB); sb.append(":");
        sb.append(ARGS4J_LIB); sb.append(":");
        sb.append(TOOLS_LIB);
        this.classPathTardis = sb.toString();
    }

    @Override
    public List<File> getExtraClassPath() {
        return Collections.emptyList();
    }

    @Override
    public void initialize(File src, File bin, List<File> classPath) {
        final StringBuilder sb = new StringBuilder();
        sb.append(bin);
        for (File f : classPath) {
            sb.append(":");
            sb.append(f);
        }
        this.classPathSUT = sb.toString();
    }

    @Override
    public void run(String cName, long timeBudget) {
        try {
            final ProcessBuilder pbuilder = new ProcessBuilder(
               "java", "-Xms16G", "-Xmx16G", 
               "-classpath", this.classPathTardis,
               "tardis.Main", 
               "-sushi_lib", SUSHI_LIB, "-jbse_lib", JBSE_LIB, "-evosuite", EVOSUITE_LIB, "-z3", Z3_BIN,
               "-use_mosa", "-num_mosa_targets", "5", 
               "-num_threads", "5", "-max_depth", "50",
               "-tmp_base", TMP_DIR, "-out", OUT_DIR, 
               "-global_time_budget_duration", Long.toString(timeBudget), "-global_time_budget_unit", "SECONDS",
               "-classes", this.classPathSUT, "-target_class", cName.replace('.', '/')
            );

            pbuilder.redirectErrorStream(true);

            final Process process;
            final InputStreamReader stdout;
            final InputStream stderr;
            final OutputStreamWriter stdin;
            process = pbuilder.start();
            stderr = process.getErrorStream();
            stdout = new InputStreamReader(process.getInputStream());
            stdin = new OutputStreamWriter(process.getOutputStream());

            final PrintStream dump = new PrintStream(new FileOutputStream(new File(TMP_DIR + "/" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date()) + ".txt"), false));
            dump.println(pbuilder.command().toString());
            final BufferedReader reader = new BufferedReader(stdout);
            String line = null;
            while ((line = reader.readLine()) != null) {
                dump.println(line);
            }
            dump.close();
            reader.close();

            process.waitFor();

            if (stdout != null) stdout.close();
            if (stdin != null) stdin.close();
            if (stderr != null) stderr.close();
            if (process !=null) process.destroy();
        } catch (IOException | InterruptedException e) {
            //does nothing
        }
    }
}
