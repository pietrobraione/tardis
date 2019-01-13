# TARDIS <img width="20%" src="img/TARDIS.png">

## About

TARDIS (meTAheuRistically-driven DynamIc Symbolic execution) is an automatic test case generator for Java programs, aimed at achieving high branch coverage. It leverages a technique called dynamic symbolic (a.k.a. "concolic") execution, to alternate symbolic execution performed with the symbolic executor [JBSE](https://pietrobraione.github.io/jbse/), with test case generation, performed by finding solutions to symbolic execution path constraints with the test case generator [EvoSuite](http://www.evosuite.org/).

TARDIS aims at preserving the main advantages of [SUSHI](https://github.com/pietrobraione/sushi) and, at the same time, improving performance when information on invariants is missing.

## Installing TARDIS

Right now TARDIS can be installed only by building it from source. Formal releases will be available when TARDIS will be more feature-ready and stable.

## Building TARDIS

TARDIS is composed by several projects, some of which are imported as git submodules, and is built with Gradle. First, you should ensure that all the dependencies are present, including Z3 (see section "Dependencies"). Then, you should clone the TARDIS git repository and init/update its submodules. If you work from the command line, this means running first `git clone`, and then `git submodule init && git submodule update`. Once done all this, run the build Gradle task by invoking `gradlew build`. 

## Dependencies

TARDIS has a lot of dependencies. It must be built using a JDK version 8 - neither less, nor more. The Gradle wrapper `gradlew` included in the repository will take care to select the right version of Gradle. Gradle will automatically resolve and use the following compile-time-only dependencies:

* [JavaCC](https://javacc.org) is used in the JBSE submodule for compiling the parser for the JBSE settings files.
* [JUnit](http://junit.org) is used in the JBSE submodule for running the test suite that comes with JBSE (in future TARDIS might come with a test suite of its own).

The runtime dependencies that are automatically resolved by Gradle and included in the build path are:

* The `tools.jar` library, that is part of every JDK 8 setup (note, *not* of the JRE).
* [Javassist](http://jboss-javassist.github.io/javassist/), that is used by JBSE for all the bytecode manipulation tasks.
* [JaCoCo](http://www.eclemma.org/jacoco/), that is used by the coverage calculator included in SUSHI-Lib.
* [ASM](http://asm.ow2.org/), that is a transitive dependency used by JaCoCo.
* [args4j](http://args4j.kohsuke.org/), that is used by TARDIS to process command line arguments.

Another runtime dependency that is included in the git project is:

* [EvoSuite](http://www.evosuite.org/); TARDIS depends on customized versions of EvoSuite that can be found in the `evosuite` subdirectory. It will not work with the EvoSuite jars that you can download from the EvoSuite web page.

There is one additional runtime dependencies that is not handled by Gradle so you will need to fix it manually. 

* JBSE needs to interact with an external numeric solver for pruning infeasible program paths. At the purpose SUSHI requires to use [Z3](https://github.com/Z3Prover/z3), that is a standalone binary and can be installed almost everywhere.

## Working under Eclipse

If you want to work (as us) under Eclipse 2018-12 for Java Developers, you are lucky: All the plugins that are necessary to import TARDIS under Eclipse and make it work are already present in the distribution. If you use another version of Eclipse you must install the egit and the Buildship plugins, both available in the Eclipse Marketplace. After that, you are ready to import TARDIS under Eclipse:

* To avoid conflicts we advise to import TARDIS under an empty workspace.
* Be sure that the default Eclipse JRE is the JRE subdirectory of a full JDK 8 setup, *not* a standalone (i.e., not part of a JDK) JRE.
* JBSE uses the reserved `sun.misc.Unsafe` class, a thing that Eclipse forbids by default. To avoid Eclipse complaining about that you must modify the workspace preferences as follows: From the main menu choose Eclipse > Preferences... under macOS, or Window > Preferences... under Windows and Linux. On the left panel select Java > Compiler > Errors/Warnings, then on the right panel open the option group "Deprecated and restricted API", and for the option "Forbidden reference (access rules)" select the value "Warning" or "Info" or "Ignore".
* Switch to the Git perspective. If you cloned the Github TARDIS repository and the submodules from the command line, you can import the clone under Eclipse by clicking under the Git Repositories view the button for adding an existing repository. Otherwise you can clone the  repository by clicking the clone button, again available under the Git Repositories view (remember to tick the box "Clone submodules"). Eclipse does *not* want you to clone the repository under your Eclipse workspace, and instead wants you to follow the standard git convention of putting the git repositories in a `git` subdirectory of your home directory. If you clone the repository from a console, please follow this standard (if you clone the repository from the Git perspective Eclipse will do this for you).
* Switch back to the Java perspective and from the main menu select File > Import... In the Select the Import Wizard window that pops up choose the Gradle > Existing Gradle Project wizard and press the Next button twice. In the Import Gradle Project window that is displayed, enter in the Project root directory field the path to the TARDIS cloned git repository, and then press the Finish button to confirm. Now your workspace should have four Java project named `jbse`, `tardis`, `sushi-lib`, and `tardis-master`.
* Unfortunately the Buildship Gradle plugin is not able to fully configure the imported projects: As a consequence, after the import you will see some compilation errors due to the fact that the JBSE project did not generate some source files yet. Fix the situation by following this procedure: In the Gradle Tasks view double-click on the tardis > build > build task to build all the projects. Then, right-click the jbse project in the Package Explorer, and in the contextual menu that pops up select Gradle > Refresh Gradle Project. After that, you should see no more errors.

In the end, your Eclipse workspace should contain these projects:

* tardis: the container project from which Gradle must be run;
* jbse: JBSE as a submodule; on the filesystem it is in the `jbse` subdirectory;
* tardis-master: the bulk of the TARDIS tool; on the filesystem it is in the `master` subdirectory;
* sushi-lib: the [sushi-lib](https://github.com/pietrobraione/sushi-lib) submodule for the run-time library component of TARDIS; on the filesystem it is in the `runtime` subdirectory.

## Deploying SUSHI

Deploying TARDIS to be used outside Eclipse is tricky but feasible with some effort. The `gradlew build` command will produce a SUSHI-Lib jar `runtime/build/libs/sushi-lib-<VERSION>.jar`, the JBSE jars in `jbse/build/libs` (refer to the JBSE project's README file for more information on them), and a jar for the main TARDIS application `master/build/libs/tardis-master-<VERSION>.jar`. You may deploy them and all the missing dependencies, if you feel adventurous. However, `gradlew build` will also produce an uber-jar `master/build/libs/tardis-<VERSION>-shaded.jar`, containing all the runtime dependencies excluded Z3, EvoSuite, and `tools.jar`. Deploying based on the uber-jar currently is the easiest way for deploying TARDIS. Moreover, although JBSE and SUSHI-Lib are already included in the TARDIS uber-jar, you will need to deploy the JBSE and SUSHI-Lib jars. The instruction for deploying based on the uber-jar plus these six dependencies are the following ones:

* You can put the TARDIS uber-jar anywhere: Just set the Java classpath to point at it.
* Deploying Z3 is very easy: Just put the Z3 binary directory somewhere, and add the Z3 binary to the system PATH, or use the `-z3` option when invoking SUSHI to point at it. 
* Deploying EvoSuite is similarly easy: Put the right EvoSuite jar somewhere, and then use the `-evosuite` option when invoking TARDIS to point at it. Since TARDIS executes EvoSuite in a separate process, you do not need to put the EvoSuite jar in the classpath. 
* TARDIS will not run if you deploy it on a machine that has a JRE, instead of a JDK, installed. This because SUSHI needs to invoke the platform's `javac` to compile some intermediate files. Therefore, you need to install a full JDK 8 on the target machine, providing both `tools.jar` and `javac` to SUSHI. Add `tools.jar` to the classpath, if it is not already in it by default.
* Finally, the JBSE and SUSHI-Lib jars need not to be on the classpath (they are included in the SUSHI uber-jar, that is already in the classpath), but the path to them must be passed to SUSHI through the `-jbse_lib` and `-sushi_lib` options. 

