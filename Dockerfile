FROM ubuntu:20.04
MAINTAINER Pietro Braione <pietro.braione@gmail.com>

# Setup base image 
RUN apt-get update -y
RUN apt-get install -y openjdk-8-jdk
RUN apt-get install -y unzip
RUN apt-get install -y nano
RUN apt-get install -y git
RUN apt-get install -y z3
RUN rm -rf /var/lib/apt/lists/*

# Setup environment variables
ENV HOME /root
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64
ENV JARS_HOME /usr/share/java
ENV Z3_HOME /usr/bin
ENV CLASSPATH ${JAVA_HOME}/lib/tools.jar:${JARS_HOME}/jbse-0.11.0-SNAPSHOT-shaded.jar:${JARS_HOME}/tardis-master-0.2.0-SNAPSHOT.jar:${JARS_HOME}/args4j-2.32.jar:${JARS_HOME}/javaparser-core-3.15.9.jar:${JARS_HOME}/log4j-api-2.14.0.jar:${JARS_HOME}/log4j-core-2.14.0.jar:${JARS_HOME}/sushi-lib-0.2.0-SNAPSHOT.jar:${JARS_HOME}/asm-debug-all-5.0.1.jar:${JARS_HOME}/org.jacoco.core-0.7.5.201505241946.jar:${JARS_HOME}/evosuite-shaded-1.2.1-SNAPSHOT.jar

# Build and install
WORKDIR ${HOME}
RUN git clone https://github.com/pietrobraione/tardis
WORKDIR ${HOME}/tardis
RUN git submodule init && git submodule update
RUN ./gradlew build
RUN cp jbse/build/libs/jbse-0.11.0-SNAPSHOT-shaded.jar ${JARS_HOME}/.
RUN cp master/build/libs/tardis-master-0.2.0-SNAPSHOT.jar ${JARS_HOME}/.
RUN cp master/deps/args4j-2.32.jar ${JARS_HOME}/.
RUN cp master/deps/javaparser-core-3.15.9.jar ${JARS_HOME}/.
RUN cp master/deps/log4j-api-2.14.0.jar ${JARS_HOME}/.
RUN cp master/deps/log4j-core-2.14.0.jar ${JARS_HOME}/.
RUN cp runtime/build/libs/sushi-lib-0.2.0-SNAPSHOT.jar ${JARS_HOME}/.
RUN cp runtime/deps/asm-debug-all-5.0.1.jar ${JARS_HOME}/.
RUN cp runtime/deps/org.jacoco.core-0.7.5.201505241946.jar ${JARS_HOME}/.
RUN cp libs/evosuite-shaded-1.2.1-SNAPSHOT.jar ${JARS_HOME}/.

# Create script
RUN echo "#!/bin/sh" > /usr/local/bin/tardis
RUN echo "java -Xms16G -Xmx16G -cp ${CLASSPATH} tardis.Main -evosuite ${JARS_HOME}/evosuite-shaded-1.2.1-SNAPSHOT.jar -jbse_lib ${JARS_HOME}/jbse-0.11.0-SNAPSHOT-shaded.jar -sushi_lib ${JARS_HOME}/sushi-lib-0.2.0-SNAPSHOT.jar -z3 ${Z3_HOME}/z3 \$@" >> /usr/local/bin/tardis
RUN chmod +x /usr/local/bin/tardis

# Get some examples and compile them
WORKDIR ${HOME}
RUN git clone https://github.com/pietrobraione/tardis-experiments
WORKDIR ${HOME}/tardis-experiments
RUN mkdir bin
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g src/array/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g src/avl/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/constants/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/string/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/stringcontains/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/stringprefix/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g src/testgen/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin tardis-src/common/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin tardis-src/array/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin tardis-src/avl/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g tardis-src/symbols/constants/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g tardis-src/symbols/string/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g tardis-src/symbols/stringcontains/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g tardis-src/symbols/stringprefix/*.java
RUN javac -cp ${CLASSPATH}:${HOME}/tardis-experiments/bin -d bin -g tardis-src/testgen/*.java

WORKDIR ${HOME}

