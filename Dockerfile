FROM openjdk:11-jdk-slim as builder
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && \
    apt-get install -y --no-install-recommends apt-transport-https apt-utils bc dirmngr gnupg && \
    echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list && \
    apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823 && \
    # seems that dash package upgrade is broken in Debian, so we hold it's version before update
    echo "dash hold" | dpkg --set-selections && \
    apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y --no-install-recommends sbt
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
