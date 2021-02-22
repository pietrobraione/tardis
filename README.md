# TARDIS <img width="20%" src="img/TARDIS.png">

## About

TARDIS (meTAheuRistically-driven DynamIc Symbolic execution) is an automatic test case generator for Java programs, aimed at achieving high branch coverage. It leverages a technique called dynamic symbolic (a.k.a. "concolic") execution, to alternate symbolic execution performed with the symbolic executor [JBSE](https://pietrobraione.github.io/jbse/), with test case generation, performed by finding solutions to symbolic execution path constraints with the test case generator [EvoSuite](http://www.evosuite.org/).

TARDIS aims at preserving the main advantages of [SUSHI](https://github.com/pietrobraione/sushi) and, at the same time, improving performance when information on invariants is missing.

## Installing TARDIS

Right now TARDIS can be installed only by building it from source. Formal releases will be available when TARDIS will be more feature-ready and stable.

## Building TARDIS

TARDIS is composed by several projects, some of which are imported as git submodules, and is built with Gradle version 6.7.1, that is included in the repository. First, ensure that all the dependencies are present, including Z3 (see section "Dependencies"). Then, clone the TARDIS git repository and init/update its submodules. If you work from the command line, this means running first `git clone`, and then `git submodule init && git submodule update`. Next, follow the instructions described in the section "Patching the tests" of the README.md file of the JBSE subproject. Finally, run the build Gradle task, e.g. by invoking `gradlew build` from the command line. 

## Dependencies

TARDIS has many dependencies. It must be built using a JDK version 8 - neither less, nor more. We suggest to use the latest [AdoptOpenJDK](https://adoptopenjdk.net/) v8 with HotSpot JVM (note that the JDK with the OpenJ9 JVM currently does not work, because there are some slight differences in the standard library classes). The Gradle wrapper `gradlew` included in the repository will take care to select the right version of Java. Gradle will automatically resolve and use the following compile-time-only dependencies:

* [JavaCC](https://javacc.org) is used in the JBSE submodule for compiling the parser for the JBSE settings files.
* [JUnit](http://junit.org) is used in the JBSE submodule for running the test suite that comes with JBSE (in future TARDIS might come with a test suite of its own).

The runtime dependencies that are automatically resolved by Gradle and included in the build path are:

* The `tools.jar` library, that is part of every JDK 8 setup (note, *not* of the JRE).
* [Javassist](http://jboss-javassist.github.io/javassist/), that is used by JBSE for all the bytecode manipulation tasks. The patched version of Javassist that is distributed with JBSE is necessary, and TARDIS will not work with an upstream Javassist version.
* [args4j](http://args4j.kohsuke.org/), that is used by TARDIS to process command line arguments.
* [JavaParser](https://javaparser.org/), that is used by TARDIS to process the source files for the generated tests.
* [Log4J 2](https://logging.apache.org/log4j/2.x/), that is used by TARDIS for logging.

Another runtime dependency that is included in the git project is:

* [EvoSuite](http://www.evosuite.org/); TARDIS depends on a customized version of EvoSuite that can be found in the `lib` subdirectory. It will not work with the EvoSuite jars that you can download from the EvoSuite web page.

There is one additional runtime dependencies that is not handled by Gradle so you will need to fix it manually. 

* JBSE needs to interact with an external numeric solver for pruning infeasible program paths. At the purpose TARDIS uses [Z3](https://github.com/Z3Prover/z3), a standalone binary that can be installed almost everywhere in your system.

Finally, two runtime dependencies that are not currently used by TARDIS at runtime, but might be in future, are:

* [JaCoCo](http://www.eclemma.org/jacoco/), that is used by the coverage calculator included in SUSHI-Lib.
* [ASM](http://asm.ow2.org/), that is a transitive dependency used by JaCoCo.

Gradle will download them to compile SUSHI-Lib, but you can avoid to deploy them.

## Working under Eclipse

If you want to modify and build TARDIS by using (as we do) Eclipse 2020-12 for Java Developers, you are lucky: All the Eclipse plugins that are necessary to import and build TARDIS are already present in the distribution. The only caveat is that, since starting from version 2020-09 Eclipse requires at least Java 11 to run, your development machine will need to have both a Java 11 (to run Eclipse) and a Java 8 setup (to build TARDIS). Gradle will automatically select the right version of the JDK when building TARDIS. If you use a different flavor, or an earlier version, of Eclipse you might need to install the egit and the Buildship plugins, both available from the Eclipse Marketplace. After that, to import TARDIS under Eclipse follow these steps:

* To avoid conflicts we advise to import TARDIS under an empty workspace.
* Be sure that the default Eclipse JRE is the JRE subdirectory of a full JDK 8 setup, *not* a standalone (i.e., not part of a JDK) JRE.
* JBSE uses the reserved `sun.misc.Unsafe` class, a thing that Eclipse forbids by default. To avoid Eclipse complaining about that you must modify the workspace preferences as follows: From the main menu choose Eclipse > Preferences... under macOS, or Window > Preferences... under Windows and Linux. On the left panel select Java > Compiler > Errors/Warnings, then on the right panel open the option group "Deprecated and restricted API", and for the option "Forbidden reference (access rules)" select the value "Warning" or "Info" or "Ignore".
* Switch to the Git perspective. If you cloned the Github TARDIS repository and the submodules from the command line, you can import the clone under Eclipse by clicking under the Git Repositories view the button for adding an existing repository. Otherwise you can clone the  repository by clicking the clone button, again available under the Git Repositories view (remember to tick the box "Clone submodules"). Eclipse does *not* want you to clone the repository under your Eclipse workspace, and instead wants you to follow the standard git convention of putting the git repositories in a `git` subdirectory of your home directory. If you clone the repository from a console, please follow this standard (if you clone the repository from the Git perspective Eclipse will do this for you).
* Switch back to the Java perspective and from the main menu select File > Import... In the Select the Import Wizard window that pops up choose the Gradle > Existing Gradle Project wizard and press the Next button twice. In the Import Gradle Project window that is displayed, enter in the Project root directory field the path to the TARDIS cloned git repository, and then press the Finish button to confirm. Now your workspace should have four Java project named `jbse`, `sushi-lib`, `tardis`, and `tardis-master`.
* Don't forget to apply all the patches described at the beginning of the "Building TARDIS" section.
* Unfortunately the Buildship Gradle plugin is not able to fully configure the imported projects: As a consequence, after the import you will see some compilation errors due to the fact that the JBSE project did not generate some source files yet. Fix the situation by following this procedure: In the Gradle Tasks view double-click on the tardis > build > build task to build all the projects. Then, right-click the jbse project in the Package Explorer, and in the contextual menu that pops up select Gradle > Refresh Gradle Project. After that, you should see no more errors.

In the end, your Eclipse workspace should contain these projects:

* tardis: the container project from which Gradle must be run;
* tardis-master: the bulk of the TARDIS tool; on the filesystem it is in the `master` subdirectory;
* sushi-lib: the [sushi-lib](https://github.com/pietrobraione/sushi-lib) submodule for the run-time library component of TARDIS; on the filesystem it is in the `runtime` subdirectory;
* jbse: JBSE as a submodule; on the filesystem it is in the `jbse` subdirectory.

## Deploying TARDIS

Deploying TARDIS outside the build environment to a target machine is tricky. The `gradlew build` command will produce a SUSHI-Lib jar `runtime/build/libs/sushi-lib-<VERSION>.jar`, the JBSE jars in `jbse/build/libs` (refer to the JBSE project's README file for more information on them), and a jar for the main TARDIS application `master/build/libs/tardis-master-<VERSION>.jar`. You need to deploy them and all their dependencies. Moreover, `gradlew build` will produce an uber-jar `master/build/libs/tardis-master-<VERSION>-shaded.jar` containing all the runtime dependencies excluded EvoSuite, `tools.jar`, and the native files (Z3). Deploying based on the uber-jar is easier, but to our experience a setup based on the uber-jar is more crash-prone (on the other hand, using the uber-jar for JBSE is safe). 

Here follow detailed instructions for deploying TARDIS based on the plain jars:

* Deploy Z3, possibly adding the Z3 binary to the system PATH.
* Deploy the `tardis-master-<VERSION>.jar` and set the Java classpath to point at it.
* Deploy either the `jbse-<version>.jar` or the `jbse-<version>-shaded.jar` and set the Java classpath to point at it.
* Deploy the `sushi-lib-<VERSION>.jar` and set the Java classpath to point at it.
* Deploy the EvoSuite jar contained in the `lib` directory. While EvoSuite is run in separate processes, TARDIS will nevertheless try to load some of the EvoSuite classes, therefore you need to put the EvoSuite jar in the classpath. 
* TARDIS requires a full JDK (not just a JRE) version 8 installed on the platform it runs. Add the `tools.jar` of the JDK 8 installed on the platform to the classpath.
* Deploy the args4j, log4j-api and log4j-core jars that you find in the Gradle cache and (if you do not use the `jbse-<version>-shaded.jar` uber-jar) the Javassist jar that you find in the `jbse/libs` directory. All these jars must be on the classpath.

If you deploy the `tardis-master-<VERSION>-shaded.jar` uber-jar you do not need to deploy the JBSE, SUSHI-Lib, args4j, Javassist and Log4J 2 jars.

## Usage

Compile the target program with the debug symbols, then launch TARDIS either from the command line, or from another program, e.g., from the `main` of an application. From the command line you need to invoke it as follows:

    $ java -Xms16G -Xmx16G -cp <classpath> tardis.Main <options>

where `<classpath>` must be set according to the indications of the previous section. (Note that TARDIS is resource-consuming, thus we increased to 16 GB the memory allocated to the JVM running it). If you launch TARDIS without options it will print a help screen that lists all the available options with a brief explanation. If you prefer to launch TARDIS from code, this is a possible template:

```Java
import tardis.Main;
import tardis.Options;

public class Launcher {
  public static void main(String[] args) {
    final Options o = new Options();
    o.set...
    final Main m = new Main(o);
    m.start();
  }
}
```

In both cases you need to set a number of options. The indispensable ones, that you *must* set in order for TARDIS to work, are:

* `-java8_home` (command line) or `setJava8Home` (code): the path to the home directory of a Java 8 full JDK setup, in case the default JDK installed on the deploy platform should be overridden. If this parameter is not provided, TARDIS will try with the default JDK installed on the deploy platform.
* `-evosuite` (command line) or `setEvosuitePath` (code): the path to the EvoSuite jar file `evosuite-shaded-1.0.6-SNAPSHOT.jar` contained in the `lib/` folder. The same jar file must be put on the classpath (see previous section).
* `-jbse_lib` (command line) or `setJBSELibraryPath` (code): this must be set to the path of the JBSE jar file from the `jbse/build/libs` directory. It must be the same you put in the classpath. If you chose to deploy the `tardis-master-<VERSION>-shaded.jar` uber-jar, set this option to point to it.
* `-sushi_lib` (command line) or `setSushiLibPath` (code): this must be set to the path of the SUSHI-Lib jar file from the `runtime/build/libs` directory.  If you chose to deploy the `tardis-master-<VERSION>-shaded.jar` uber-jar, set this option to point to it.
* `-z3` (command line) or `setZ3Path` (code): the path to the Z3 binary.
* `-classes` (command line) or `setClassesPath` (code): a colon- or semicolon-separated (depending on the OS) list of paths; It is the classpath of the software under test.
* `-target_class` (command line) or `setTargetClass` (code): the name in [internal classfile format](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2.1) of the class to test: TARDIS will generate tests for all the methods in the class. Or alternatively:
* `-target_method` (command line) or `setTargetMethod` (code): the signature of a method to test. The signature is a colon-separated list of: the name of the container class in internal classfile format; the [descriptor](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3) of the method; the name of the method. You can use the `javap` command, included with every JDK setup, to obtain the internal format signatures of methods: `javap -s my.Class` prints the list of all the methods in `my.Class` with their signatures in internal format.
* `-tmp_base` (command line) or `setTmpDirectoryBase` (code): a path to a temporary directory; TARDIS needs to create many intermediate files, and will put them in a subdirectory of the one that you specify with this option. The subdirectory will have as name the date and time when TARDIS was launched.
* `-out` (command line) or `setOutDirectory`: a path to a directory where TARDIS will put the generated tests.

You will find examples of the code-based way of configuring TARDIS in the [tardis-experiments](https://github.com/pietrobraione/tardis-experiments) project. A possible example of command line is the following:

    java -Xms16G -Xmx16G -cp tardis-master-0.2.0-SNAPSHOT.jar:sushi-lib-0.2.0-SNAPSHOT.jar:jbse-0.9.0-SNAPSHOT-shaded.jar:evosuite-shaded-1.0.6-SNAPSHOT.jar:args4j-2.32.jar:log4j-api-2.14.0.jar:log4j-core-2.14.0.jar:/usr/lib/jvm/java-8-openjdk-amd64/lib/tools.jar tardis.Main -jbse_lib jbse-0.9.0-SNAPSHOT-shaded.jar -sushi_lib sushi-lib-0.2.0-SNAPSHOT.jar -evosuite evosuite-shaded-1.0.6-SNAPSHOT.jar -z3 /opt/local/bin/z3 -classes ./my-application/bin -target_class my/Class -tmp_base ./tmp -out ./tests
    
In the case you prefer (at your own risk) to use the TARDIS uber-jar the command line becomes a bit shorter:

    java -Xms16G -Xmx16G -cp tardis-master-0.2.0-SNAPSHOT-shaded.jar:evosuite-shaded-1.0.6-SNAPSHOT.jar:/usr/lib/jvm/java-8-openjdk-amd64/lib/tools.jar tardis.Main -jbse_lib tardis-master-0.2.0-SNAPSHOT-shaded.jar -sushi_lib tardis-master-0.2.0-SNAPSHOT-shaded.jar -evosuite evosuite-shaded-1.0.6-SNAPSHOT.jar -z3 /opt/local/bin/z3 -classes ./my-application/bin -target_class my/Class -tmp_base ./tmp -out ./tests

## Generated tests

The tests are generated in EvoSuite format, where a test suite is composed by two classes: a scaffolding class, and the class containing all the test cases (the actual suite). TARDIS will produce many suites each containing exactly one test case: If, e.g., a run of TARDIS generates 10 test cases, then in the directory indicated with the `-out` command line parameter you will find 10 scaffolding classes, and 10 actual test suite classes each containing exactly 1 test case. Note that you do *not* need the scaffolding classes to compile and run the tests in the test suite classes, but these depend on JUnit and on the EvoSuite jar. You can safely remove the latter dependency by manually editing the generated files, otherwise you need to put the EvoSuite jar used to generate the tests in the classpath when compiling and running the generated test suites.

The generated files have names structured as follows:
    
    <class name>_<number>_Test_scaffolding.java //the scaffolding class
    <class name>_<number>_Test.java             //the actual suite class

where `<class name>` is the name of the class under test, and `<number>` is a sequential number that distinguishes the different generated classes, and that roughly reflects in which order the tests were generated.

The generated scaffolding/actual suite classes are declared in the same package as the class under test, so they can access its package-level members. This means, for example, that if you have specified the option `-out /your/out/dir`, an `avl_tree.AvlTree` class under test will produce a test `/your/out/dir/avl_tree/AvlTree_1_Test.java`. If you want to compile and execute the test suites add the output directory to the classpath and qualify the class name of the test suite with the package name, e.g.:

    $ javac -cp junit.jar:evosuite-shaded-1.0.6-SNAPSHOT.jar:avltree.jar
        /your/out/dir/avl_tree/AvlTree_1_Test.java
    $ java -cp junit.jar:evosuite-shaded-1.0.6-SNAPSHOT.jar:avltree.jar:/your/out/dir
        org.junit.runner.JUnitCore avl_tree.AvlTree_1_Test

## Disclaimer

TARDIS is a research prototype. As such, it is more focused on functionality than on usability. We are committed to progressively improving the situation.
