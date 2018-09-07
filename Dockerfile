# Description:
#   Builds an image to be used when developing in Java. The default CMD is to run
#   build_java.
#
# Build:
#   $ cd sawtooth-sdk-java
#   $ docker build . -t sawtooth-sdk-java
#
# Run:
#   $ cd sawtooth-sdk-java
#   $ docker run -v $(pwd):/project/sawtooth-sdk-java sawtooth-sdk-java

FROM maven:3-jdk-8

LABEL "install-type"="mounted"

EXPOSE 4004/tcp

RUN mkdir -p /project/sawtooth-sdk-java/ \
 && mkdir -p /var/log/sawtooth \
 && mkdir -p /var/lib/sawtooth \
 && mkdir -p /etc/sawtooth \
 && mkdir -p /etc/sawtooth/keys

ENV PATH=$PATH:/project/sawtooth-sdk-java/bin

WORKDIR /

ADD target/legerapp-1.0-SNAPSHOT.jar .
ADD log4j.properties .

CMD /project/sawtooth-sdk-java/bin/build_java_sdk \
 && /project/sawtooth-sdk-java/bin/build_java_intkey \
 && /project/sawtooth-sdk-java/bin/build_java_xo
