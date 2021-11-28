FROM openjdk:11-jdk-slim as builder
ENV DEBIAN_FRONTEND noninteractive
ARG SBT_VERSION=1.4.9
# Install sbt
RUN apt-get update -y && apt-get install -y curl
RUN \
  mkdir /working/ && \
  cd /working/ && \
  curl -L -o sbt-$SBT_VERSION.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  cd && \
  rm -r /working/ && \
  sbt sbtVersion
COPY ["build.sbt", "/assembler/"]
COPY ["project", "/assembler/project"]
RUN sbt -Dsbt.rootdir=true update
COPY . /assembler
WORKDIR /assembler
RUN sbt -Dsbt.rootdir=true assembly
RUN mv `find . -name ergo-assembler-*.jar` /ergo-assembler.jar
CMD ["java", "-jar", "/ergo.jar"]
FROM openjdk:11-jre-slim
RUN adduser --disabled-password --home /home/ergo --uid 9052 --gecos "ErgoPlatform" ergo && \
    install -m 0750 -o ergo -g ergo -d /home/ergo/.ergo
COPY --from=builder /ergo-assembler.jar /home/ergo/ergo-assembler.jar
USER ergo
EXPOSE 8080
WORKDIR /home/ergo
VOLUME ["/home/ergo/.ergo"]
ENTRYPOINT java -jar -Dconfig.file=application.conf /home/ergo/ergo-assembler.jar
CMD []
