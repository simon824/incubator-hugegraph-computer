FROM openjdk:11
LABEL maintainer="HugeGraph Docker Maintainers <hugegraph@googlegroups.com>"
# use ParallelGC is more friendly to olap system
ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseContainerSupport -XX:MaxRAMPercentage=50"
COPY . /etc/local/hugegraph-computer
WORKDIR /etc/local/hugegraph-computer
RUN apt-get update && apt-get -y install gettext-base && apt-get -y install wget && apt-get -y install net-tools && apt-get -y install unzip
RUN wget https://github.com//alibaba/arthas/releases/download/arthas-all-3.5.4/arthas-bin.zip
RUN unzip arthas-bin.zip
