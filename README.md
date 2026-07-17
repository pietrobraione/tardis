# TARDIS <img width="10%" src="img/TARDIS.png">

## About

TARDIS (meTAheuRistically-driven DynamIc Symbolic execution) is an automatic test case generator for Java programs, aimed at achieving high branch coverage. It leverages a technique called dynamic symbolic (a.k.a. "concolic") execution, to alternate symbolic execution performed with the symbolic executor [JBSE](https://pietrobraione.github.io/jbse/), with test case generation, performed by finding solutions to symbolic execution path constraints with the test case generator [EvoSuite](http://www.evosuite.org/).

TARDIS aims at preserving the main advantages of [SUSHI](https://github.com/pietrobraione/sushi) and, at the same time, improving performance when information on invariants is missing.

## Installing TARDIS

There are two ways to install TARDIS. The easiest is via Docker; The less easy is by building it from source and deploying it on your local machine. In both cases we support only the head revision of the master branch. Formal releases will be available when TARDIS will be more feature-ready and stable.

### Installing TARDIS via Docker

A convenient package is available from the TARDIS GitHub page, that allows you to install a Docker image containing a setup of TARDIS and some examples to play with. From the command line run:

    $ docker pull ghcr.io/pietrobraione/tardis:master

Alternatively, download the `Dockerfile` to the current directory and from the command line run:

    $ docker build -t tardis .
    $ docker run -it tardis
   
The resulting environment is an Ubuntu container, where at the current (`/root`) directory you will find a clone of the head revision of the master branch of the TARDIS and of the [tardis-experiments](https://github.com/pietrobraione/tardis-experiments) repositories.

### Building TARDIS

TARDIS is composed by several projects, one of which (JBSE) is imported as git submodule, and is built with Gradle version 8.14.5, whose wrapper is included in the repository. First, ensure that all the dependencies are present, including Z3 (see section "Dependencies"). Then, clone the TARDIS git repository and init/update its submodules. If you work from the command line, this means running first `git clone`, and then `git submodule init && git submodule update`. Next, follow the instructions described in the section "Patching the tests" of the README.md file of the JBSE subproject. Finally, run the build Gradle task, e.g. by invoking `./gradlew build` from the command line in the TARDIS project directory. 

#### Dependencies

TARDIS has many dependencies. It must be built using a JDK version 8 - neither less, nor more. Unfortunately we found issues running Gradle version 8.14.5 on Java version 8, so you will also need at least a JRE with a later version (in our experience version 21.0.2 is ok). We suggest to use the latest Eclipse Temurin from [Adoptium](https://adoptium.net/) with HotSpot JVM (note that the JDK with the OpenJ9 JVM currently does not work, because there are some slight differences in the standard library classes). If you are on an Apple Silicon Mac we suggest to use the JDK Zulu distribution from [Azul](https://www.azul.com/downloads/?package=jdk#zulu). The Gradle wrapper `gradlew` included in the repository will take care to select the right version of Java when building TARDIS. Gradle will also automatically resolve and use the following compile-time-only dependencies:

* [JavaCC](https://javacc.org), used in the JBSE submodule for compiling the parser for the JBSE settings files, and
* [JUnit](http://junit.org), used in the JBSE submodule for running the test suite that comes with JBSE (in future TARDIS might come with a test suite of its own).

The runtime dependencies that are automatically resolved by Gradle and included in the build path are:

* The `tools.jar` library, that is part of every JDK 8 setup (note, *not* of a JRE).
* [Javassist](http://jboss-javassist.github.io/javassist/), that is used by JBSE for all the bytecode manipulation tasks.
* [args4j](http://args4j.kohsuke.org/), that is used by TARDIS to process command line arguments.
* [JavaParser](https://javaparser.org/), that is used by TARDIS to process the source files for the generated tests.
* [Log4J 2](https://logging.apache.org/log4j/2.x/), that is used by TARDIS for logging.

Another runtime dependency that is included in the git project is:

* [EvoSuite](http://www.evosuite.org/); TARDIS depends on a customized version of EvoSuite that can be found in the `libs` subdirectory. It will not work with the upstream EvoSuite versions that you can download from the EvoSuite web page.

There is one additional runtime dependencies that is not handled by Gradle so you will need to fix it manually. 

* JBSE needs to interact with an external numeric solver for pruning infeasible program paths. At the purpose TARDIS uses [Z3](https://github.com/Z3Prover/z3), that comes in the shape of standalone binaries for all the platforms that can be installed almost everywhere in your system.

#### Working under Eclipse

If you want to build (and possibly modify) TARDIS by using (as we do) the latest Eclipse for Java Developers, you are lucky: All the Eclipse plugins that are necessary to import and build TARDIS are already present in the distribution. The only caveat is that the last Eclipse versions, the most recent currently being 2026-06, come with their own JRE on which Eclipse runs. This JRE is also used by the Buildship Eclipse plugin to run Gradle. Version 8.14.5 of Gradle happily runs on the JRE that comes with the current version of Eclipse, so if you use Eclipse perhaps you do not strictly need to install a Java 21 JRE (but you still need to install a Java 8 JDK to build and run JBSE and TARDIS). If you use a different flavor of Eclipse you might need to install the egit and the Buildship plugins, both available from the Eclipse Marketplace. After that, to import TARDIS under Eclipse follow these steps:

* To avoid conflicts we advise to import TARDIS under an empty workspace.
* Be sure that the default Eclipse JRE for your workspace, the one that will be used to run your projects, is the JRE subdirectory of a full JDK 8 setup, *not* a standalone (i.e., not part of a JDK) JRE: Unfortunately, Eclipse looks for the Java 8 compiler based on the path of the Java 8 JRE. Do it as follows: From the main menu choose Eclipse > Preferences... under macOS, or Window > Preferences... under Windows and Linux. On the left panel select Java > Compiler and on the right combo box "Compiler compliance level" select "1.8". Then on the left panel select Java > Installed JREs... and on the right list tick the row corresponding to your JDK 8 setup (if it is not present, add it by pressing the "Add..." button).
* JBSE uses the reserved `sun.misc.Unsafe` class, a thing that Eclipse forbids by default. To avoid Eclipse complaining about that you must modify the workspace preferences as follows: From the main menu choose Eclipse > Preferences... under macOS, or Window > Preferences... under Windows and Linux. On the left panel select Java > Compiler > Errors/Warnings, then on the right panel open the option group "Deprecated and restricted API", and for the option "Forbidden reference (access rules)" select the value "Warning" or "Info" or "Ignore".
* Switch to the Git perspective. If you cloned the Github TARDIS repository and the submodules from the command line, you can import the clone under Eclipse by clicking under the Git Repositories view the button for adding an existing repository. Otherwise you can clone the repository directly under Eclipse by clicking the clone button, again available under the Git Repositories view (remember to tick the box "Clone submodules"). Eclipse does *not* want you to clone the repository under your Eclipse workspace, and instead wants you to follow the standard git convention of putting the git repositories in a `git` subdirectory of your home directory. If you clone the repository from the console please follow this standard (if you clone the repository from the Git perspective Eclipse will do this for you).
* Switch back to the Java perspective and from the main menu select File > Import... In the Select the Import Wizard window that pops up choose the Gradle > Existing Gradle Project wizard and press the Next button until the Import Gradle Project window is displayed. Then enter in the Project root directory field the path to the TARDIS cloned git repository, and press the Finish button to confirm. Now your workspace should have three Java project named `jbse`, `tardis`, and `tardis-master`.
* Don't forget to apply all the patches as described at the beginning of the "Building TARDIS" section.
* Unfortunately the Buildship Gradle plugin is not able to fully configure the imported projects: As a consequence, after the import you will see some compilation errors due to the fact that the JBSE project did not generate some necessary source files. Fix the situation by following this procedure: In the Gradle Tasks view double-click on the tardis > build > build task to build all the projects. Then, right-click the jbse project in the Package Explorer, and in the contextual menu that pops up select Gradle > Refresh Gradle Project. After that, you should see no more errors.

In the end, your Eclipse workspace should contain these projects:

* tardis: the container project from which Gradle must be run to build everything;
* tardis-master: the bulk of the TARDIS tool implementation; on the filesystem it is in the `master` subdirectory;
* jbse: JBSE as a submodule; on the filesystem it is in the `jbse` subdirectory.

#### Deploying TARDIS

Deploying TARDIS outside the build environment to a target machine is tricky. The `gradlew build` command will produce a JBSE jar `jbse/build/libs/jbse-<version>.jar`, and a jar for the main TARDIS application `master/build/libs/tardis-master-<VERSION>.jar`. Moreover, it will copy all the (jar) runtime dependencies of the JBSE and TARDIS projects in `jbse/deps`, and `master/deps` respectively. Finally, in the `libs` directory of the container TARDIS project you will find two more jars from which TARDIS depends: `evosuite-shaded-<version>-SNAPSHOT.jar`, our modified version of EvoSuite, and `sushi-lib-<version>-SNAPSHOT.jar`. To learn about [SUSHI-lib](https://github.com/pietrobraione/sushi-lib/) see the README file on its Github project. You need to deploy all these jars plus the native files (Z3). The build process will also produce an uber-jar `master/build/libs/tardis-master-<VERSION>-shaded.jar` containing all the runtime jar dependencies excluded EvoSuite, SUSHI-lib, and `tools.jar`. Deploying based on the TARDIS uber-jar is easier, but to our experience a setup based on the TARDIS uber-jar is more crash-prone. On the other hand, the build process will also produce a JBSE uber-jar whose use is safe; You find it as `jbse/build/libs/jbse-<version>-shaded.jar`. This uber-jar contains the only runtime dependency of JBSE, i.e., the Javassist jar. 

Here follow detailed instructions for deploying TARDIS:

* Deploy Z3, possibly adding the Z3 binary to the system PATH.
* Deploy the `tardis-master-<VERSION>.jar` and set the Java classpath to point at it.
* Deploy either the `jbse-<version>.jar` or the `jbse-<version>-shaded.jar` and set the Java classpath to point at it. In the first case, you must also deploy the Javassist jar that you find in the Gradle cache. You will find a copy of it in the `jbse/libs` directory. Set the Java classpath to point at the Javassist jar in the case you have installed it.
* Deploy the `evosuite-shaded-<version>-SNAPSHOT.jar` jar contained in the `libs` directory. While EvoSuite is run in separate processes, TARDIS will nevertheless try to load some of the EvoSuite classes, therefore you need to put the EvoSuite jar in the Java classpath (and alse to pass its path as a parameter to TARDIS, as we will see).
* Deploy the `sushi-lib-<VERSION>.jar` contained in the `libs` directory. Differently from the EvoSuite jar, TARDIS will not load SUSHI-lib (EvoSuite will), so you do not need to put it in the Java classpath (but you will need to pass its path as a parameter to TARDIS, as we will see).
* TARDIS requires a full JDK (not just a JRE) version 8 installed on the platform it runs. Add the `tools.jar` of the JDK 8 installed on the platform to the classpath since Java does not automatically load `tools.jar` as it does with the standard libraries (you will find a copy of `tools.jar` in `master/deps`, but it is better not to rely on it and rely instead on the original jar in the JDK home).
* Deploy the args4j jar that you find in the Gradle cache. You will find a copy of it in the `master/deps` directory. This jar must be in the Java classpath.
* Deploy the javaparser-core jar that you find in the Gradle cache. You will find a copy of it in the `master/deps` directory. This jar must be in the Java classpath.
* Deploy the log4j-api jar that you find in the Gradle cache. You will find a copy of it in the `master/deps` directory. This jar must be in the Java classpath.
* Deploy the log4j-core jar that you find in the Gradle cache. You will find a copy of it in the `master/deps` directory. This jar must be in the Java classpath.

You can study the `Dockerfile` as an example of an automatic deployment workflow on Ubuntu.

If you choose to deploy the TARDIS uber-jar `tardis-master-<VERSION>-shaded.jar` you do not need to deploy the JBSE, args4j, Javaparser, Javassist and the two Log4J 2 jars.

## Usage

Compile the program to test with a Java 8 compiler, being careful to include the debug symbols in the classfiles, then launch TARDIS with a Java 8 virtual machine, either from the command line or from another Java program. In the first case you need to invoke it as follows:

    $ java -Xms16G -Xmx16G -cp <classpath> tardis.Main <options>

where `<classpath>` must be set according to the indications of the previous section. (Note that TARDIS is resource-consuming, thus we increased to 16 GB the memory allocated to the JVM running it).
If you prefer to invoke TARDIS programmatically, this is a possible template of a launcher class:

```Java
import tardis.Main;
import tardis.Options;

public final class Launcher {
  public static void main(String[] args) {
    final Options o = new Options();
    o.setZ3Path(...);
    o.setTargetClass(...);
    ...
    final Main m = new Main(o);
    m.start();
  }
}
```

As exemplified above, the launcher application must create a `tardis.Options` object, configure it with the necessary parameters for a TARDIS execution, then create a `tardis.Main` object passing to its constructor the previous `tardis.Options` object. Finally, it must invoke the `tardis.Main.start()` method. Note that the command line TARDIS launcher is not much different from this example launcher, the main difference being that the command line launcher parses the command line arguments to create the `tardis.Options` object.

Shall you launch TARDIS via the command line or programmatically, you will need to set a number of parameters for it to work. The indispensable ones, that you *must* set in order to obtain a result, are:

* `-java8_home` (command line) or `setJava8Home` (`tardis.Options`): the path to the home directory of a Java 8 full JDK setup, in case the default JDK installed on the deploy platform is not Java 8, or should be overridden. If this parameter is not provided, TARDIS will try with the default JDK installed on the deploy platform, and in case this is not a Java 8 JDK it will fail.
* `-evosuite` (command line) or `setEvosuitePath` (`tardis.Options`): the path to the EvoSuite jar file `evosuite-shaded-<version>-SNAPSHOT.jar` contained in the `libs/` folder. It must be the same jar file you put put in the classpath (see previous section).
* `-jbse_lib` (command line) or `setJBSELibraryPath` (`tardis.Options`): this must be set to the path of the JBSE jar file from the `jbse/build/libs` directory. It must be the same jar file you put in the classpath. If you chose to deploy the TARDIS uber-jar `tardis-master-<VERSION>-shaded.jar`, set this option to point to it.
* `-sushi_lib` (command line) or `setSushiLibPath` (`tardis.Options`): this must be set to the path of the SUSHI-lib jar file from the `libs` directory.
* `-z3` (command line) or `setZ3Path` (`tardis.Options`): the path to the Z3 binary for your platform.
* `-classes` (command line) or `setClassesPath` (`tardis.Options`): a colon- or semicolon-separated (depending on the OS) list of paths; It is the classpath of the program under test.
* `-target_class` (command line) or `setTargetClass` (`tardis.Options`): the name in [internal classfile format](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2.1) of the class to test: TARDIS will generate tests for covering all the methods in the class. Or alternatively:
* `-target_method` (command line) or `setTargetMethod` (`tardis.Options`): the signature of a method to test, in which case TARDIS will generate tests for covering just this method. A signature is a colon-separated list of: the name of the container class in internal classfile format; the [descriptor](https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3) of the method; the name of the method. You can use the `javap` command, included with every JDK setup, to obtain the signatures of the methods in a class: For instance, `javap -s my.Class` prints the list of all the nonprivate methods in `my.Class` with their signatures.
* `-tmp_base` (command line) or `setTmpDirectoryBase` (`tardis.Options`): a path to a temporary directory; During its execution TARDIS creates many intermediate files, that it puts in a subdirectory of the one that you specify with this option. The subdirectory will have as name the date and time when TARDIS was launched.
* `-out` (command line) or `setOutDirectory` (`tardis.Options`): a path to a directory where TARDIS will put the generated tests. Note that TARDIS will *not* clean the content of the directory at its start: So, if you use the same output directory across many TARDIS runs you may want to consider doing it by yourself.
* `-evosuite_no_dependency` (command line) or `setEvosuiteNoDependency` (`tardis.Options`): when active, the generated test classes will depend on JUnit alone, not on the EvoSuite jar (i.e., no scaffolding class will be generated, see later).

There are many more options that allow to control several aspects of the TARDIS behaviour. You can see a synthetic description of all of them by invoking TARDIS from the command line with the `-help` option, or by reading the comments in the `tardis.Options` class.

A possible example of command line invocation is the following:

    $ java -Xms16G -Xmx16G -cp /usr/lib/jvm/java-8-openjdk-amd64/lib/tools.jar:./libs/tardis-master-0.3.0-SNAPSHOT.jar:./libs/jbse-0.12.0-SNAPSHOT-shaded.jar:./libs/evosuite-shaded-1.2.1-SNAPSHOT.jar:./libs/args4j-2.32.jar:./libs/log4j-api-2.14.0.jar:./libs/log4j-core-2.14.0.jar:./libs/javaparser-core-3.15.9.jar tardis.Main -jbse_lib ./libs/jbse-0.12.0-SNAPSHOT-shaded.jar -sushi_lib ./libs/sushi-lib-0.3.0-SNAPSHOT.jar -evosuite ./libs/evosuite-shaded-1.2.1-SNAPSHOT.jar -z3 /usr/bin/z3 -classes ./my-application/bin -target_class my/Class -tmp_base ./tmp -out ./tests
    
where we assume that all the jars except for `tools.jar` have been deployed in `./libs`, that the program under test is in `./my-application/bin`, that we want to generate tests for all the nonprivate methods in `my.Class`, that the work directory where TARDIS must put its intermediate files is `./tmp`, and that TARDIS must emit the generated tests in `./tests`. In the case you prefer (at your own risk) to use the TARDIS uber-jar the command line invocation becomes a bit, but not that much, shorter:

    $ java -Xms16G -Xmx16G -cp /usr/lib/jvm/java-8-openjdk-amd64/lib/tools.jar:./libs/tardis-master-0.3.0-SNAPSHOT-shaded.jar:./libs/evosuite-shaded-1.2.1-SNAPSHOT.jar tardis.Main -jbse_lib ./libs/tardis-master-0.3.0-SNAPSHOT-shaded.jar -sushi_lib ./libs/sushi-lib-0.3.0-SNAPSHOT.jar -evosuite ./libs/evosuite-shaded-1.2.1-SNAPSHOT.jar -z3 /usr/bin/z3 -classes ./my-application/bin -target_class my/Class -tmp_base ./tmp -out ./tests

There is a third way of launching TARDIS that mixes the two approaches: You can launch it from the command line, but by configuring the options through an object of class `tardis.Options`. At the purpose you must define a class implementing the interface `tardis.OptionsConfigurator`. This interface declares only one method `configure`, that you must override to configure a `tardis.Options` object as in the following example:

```Java
import tardis.Options;
import tardis.OptionsConfigurator;

public final class MyConfigurator implements OptionsConfigurator {
  @Override
  public void configure(Options o) {
    o.setZ3Path(...);
    o.setTargetClass(...);
    ...
  }
}
```

As you can see, the resulting code resembles that of a TARDIS launcher, but you do not need to explicitly create the `tardis.Options` object (it is received as a parameter) nor to create the `tardis.Main` object and start it. Once created your configurator class, compile it (remember to put the `tardis-master` jar in the compilation classpath), put the generated classfile where you prefer and invoke TARDIS as follows:

    $ java -Xms16G -Xmx16G -cp /usr/lib/jvm/java-8-openjdk-amd64/lib/tools.jar:./libs/tardis-master-0.3.0-SNAPSHOT.jar:./libs/jbse-0.12.0-SNAPSHOT-shaded.jar:./libs/evosuite-shaded-1.2.1-SNAPSHOT.jar:./libs/args4j-2.32.jar:./libs/log4j-api-2.14.0.jar:./libs/log4j-core-2.14.0.jar:./libs/javaparser-core-3.15.9.jar tardis.Main -options_config_path ./my-config -options_config_class MyConfigurator
    
where:

* `-options_config_path` (command line) or `setOptionsConfiguratorPath` (`tardis.Options`) is a classpath (actually, a single path) where the options configurator class must be found, and
* `-options_config_class` (command line) or `setOptionsConfiguratorClass` (`tardis.Options`) is the name in internal classfile format of the options configurator class

(in the above example we put the `MyConfigurator.class` classfile in the `./my-config` directory). You will find examples of both launcher and configurator classes in the [tardis-experiments](https://github.com/pietrobraione/tardis-experiments) project. 

### Running TARDIS from the Docker environment

Under the Docker environment you can find a more convenient `tardis` script that is installed on the `PATH`. When invoked, it runs java, passes to it the correct classpath and memory flags, adds some of the indispensable TARDIS options to your command line arguments (more precisely, the correct values for `-java8_home`, `-evosuite`,  `-jbse_lib`, `-sushi_lib` and `-z3`), and starts `tardis.Main.main(String[])` passing to it the enhanced command line. This allows you to invoke TARDIS as follows:

    $ tardis <options>

relieving you from most of the boilerplate. This way the previous example commands become much shorter:

    $ tardis -classes ./my-application/bin -target_class my/Class -tmp_base ./tmp -out ./tests
    
with the options on the command line, and

    $ tardis -options_config_path ./my-config -options_config_class MyConfigurator
    
with the configurator classes. The `tardis` script is at `/usr/local/bin` in the case you want to study it.

If you want to run TARDIS on the pre-built tardis-experiments subjects included in the Docker image you can exploit the configurators and the launchers included in the tardis-experiments project. For instance, if you want to generate tests for the AVL tree example, you can run the following command using the configurator:

    $ tardis -options_config_path /root/tardis-experiments/bin -options_config_class avl.AvlConfigurator
    
or you can invoke the launcher as follows:

    $ java -cp ${CLASSPATH}:/root/tardis-experiments/bin avl.RunAvl
    
See the `README.md` file of the tardis-experiment project for more information on where the configurators and the launchers are. TARDIS will put the generated tests in `/root/tardis-experiments/tardis-test` and the intermediate files in a subdirectory of `/root/tardis-experiments/tardis-out`.

## Generated tests

The generated tests are in EvoSuite format, where a EvoSuite test suite is composed by two classes: a scaffolding class, and a class containing the actual test cases (the test class). In case the `-evosuite_no_dependency` option is active, the scaffolding class will not be generated, and the suite will be composed by the test class only: Note, however, that without scaffolding, according to the EvoSuite documentation, the resulting tests are at a greater chance to be flaky. TARDIS will produce many EvoSuite suites each containing exactly one test case: If, e.g., a run of TARDIS generates 10 test cases, then in the directory indicated with the `-out` command line parameter you will find 10 Evosuite Suites with 10 scaffolding classes and 10 test classes containing exactly 1 test case each. Note that all these classes depend on JUnit 4 and (only the scaffolding classes) on the EvoSuite jar, therefore you will need to put these dependency on the classpath when compiling and running the generated test suites. Of course, if the `-evosuite_no_dependency` option was active when you launched TARDIS, the generated test suites will not depend on the EvoSuite jar.

The names of the generated files are structured as follows:
    
    <class name>_<number>_Test_scaffolding.java //the scaffolding class
    <class name>_<number>_Test.java             //the test class

where `<class name>` is the name of the class under test, and `<number>` is a sequential number that identifies the test, and that roughly reflects in which order the tests were generated.

The generated scaffolding/test classes are declared in the same package as the class containing the method(s) under test, so they can access its package-level members. This means, for example, that if you have specified the option `-out /your/out/dir`, and `avl_tree.AvlTree` is the class under test, TARDIS will produce, e.g., a test `/your/out/dir/avl_tree/AvlTree_1_Test.java`. If you want to execute the test, be careful to add the output directory to the classpath and qualify the name of the class with the corresponding package name (this is not strictly necessary when compiling the class):

    $ javac -cp junit.jar:evosuite-shaded-1.2.1-SNAPSHOT.jar:avltree.jar /your/out/dir/avl_tree/AvlTree_1_Test.java
    $ java -cp junit.jar:evosuite-shaded-1.2.1-SNAPSHOT.jar:avltree.jar:/your/out/dir org.junit.runner.JUnitCore avl_tree.AvlTree_1_Test

## Disclaimer

TARDIS is a research prototype. As such, it is more focused on functionality than on usability. We are committed to progressively improving the situation.
