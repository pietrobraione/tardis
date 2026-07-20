FROM ubuntu:26.04
MAINTAINER Pietro Braione <pietro.braione@gmail.com>

# Setup base image 
RUN apt-get update -y
RUN apt-get install -y openjdk-8-jdk
RUN apt-get install -y openjdk-21-jdk
RUN apt-get install -y java-common
RUN apt-get install -y unzip
RUN apt-get install -y nano
RUN apt-get install -y git
RUN apt-get install -y z3
RUN rm -rf /var/lib/apt/lists/*

# Setup environment variables
ENV HOME=/root
ENV JAVA_HOME_8=/usr/lib/jvm/java-8-openjdk-amd64
ENV JAVA_HOME_21=/usr/lib/jvm/java-21-openjdk-amd64
ENV JARS_HOME=/usr/share/java
ENV Z3_HOME=/usr/bin
ENV JARS=${JARS_HOME}/jbse-0.12.0-SNAPSHOT-shaded.jar:${JARS_HOME}/tardis-master-0.3.0-SNAPSHOT.jar:${JARS_HOME}/args4j-2.32.jar:${JARS_HOME}/javaparser-core-3.15.9.jar:${JARS_HOME}/log4j-api-2.14.0.jar:${JARS_HOME}/log4j-core-2.14.0.jar:${JARS_HOME}/evosuite-shaded-1.2.1-SNAPSHOT.jar
ENV CLASSPATH_8=${JAVA_HOME_8}/lib/tools.jar:${JARS}

# Use Java 21
ENV JAVA_HOME=${JAVA_HOME_21}

# Build and install
WORKDIR ${HOME}
RUN git clone https://github.com/pietrobraione/tardis
WORKDIR ${HOME}/tardis
RUN git submodule init && git submodule update
RUN ./gradlew build
RUN cp jbse/build/libs/jbse-0.12.0-SNAPSHOT-shaded.jar ${JARS_HOME}/.
RUN cp master/build/libs/tardis-master-0.3.0-SNAPSHOT.jar ${JARS_HOME}/.
RUN cp master/deps/args4j-2.32.jar ${JARS_HOME}/.
RUN cp master/deps/javaparser-core-3.15.9.jar ${JARS_HOME}/.
RUN cp master/deps/log4j-api-2.14.0.jar ${JARS_HOME}/.
RUN cp master/deps/log4j-core-2.14.0.jar ${JARS_HOME}/.
RUN cp libs/sushi-lib-0.3.0-SNAPSHOT.jar ${JARS_HOME}/.
RUN cp libs/evosuite-shaded-1.2.1-SNAPSHOT.jar ${JARS_HOME}/.

# Use Java 8
RUN update-java-alternatives --set java-1.8.0-openjdk-amd64

# Create script
RUN echo "#!/bin/sh" > /usr/local/bin/tardis
RUN echo "java -Xms16G -Xmx16G -cp ${CLASSPATH_8} tardis.Main -evosuite ${JARS_HOME}/evosuite-shaded-1.2.1-SNAPSHOT.jar -jbse_lib ${JARS_HOME}/jbse-0.12.0-SNAPSHOT-shaded.jar -sushi_lib ${JARS_HOME}/sushi-lib-0.3.0-SNAPSHOT.jar -z3 ${Z3_HOME}/z3 \$@" >> /usr/local/bin/tardis
RUN chmod +x /usr/local/bin/tardis

# Get some examples and compile them
WORKDIR ${HOME}
RUN git clone https://github.com/pietrobraione/tardis-experiments
WORKDIR ${HOME}/tardis-experiments
RUN mkdir bin
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/array/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/avl/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/constants/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/string/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/stringcontains/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/symbols/stringprefix/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/synergy/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin -g src/testgen/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/common/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/array/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/avl/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/symbols/constants/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/symbols/string/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/symbols/stringcontains/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/symbols/stringprefix/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/synergy/*.java
RUN javac -cp ${CLASSPATH_8}:${HOME}/tardis-experiments/bin -d bin tardis-src/testgen/*.java

WORKDIR ${HOME}

