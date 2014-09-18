FROM centos:centos6
MAINTAINER Gabe Conradi <gabe@tumblr.com>
#NOTE: you should use vendorize this container by deploying your own production.conf to /opt/collins/conf/production.conf
# as well as other configs, like profiles.yaml, permissions.yaml, users.conf, database.conf, validations.conf, and authentication.conf

RUN yum install -y wget zip unzip git java-1.6.0-openjdk java-1.6.0-openjdk-devel && yum -y clean all
RUN useradd -Ur -d /opt/collins collins
RUN for dir in /build /var/log/collins /var/run/collins; do mkdir $dir; chown collins $dir; done
ENV JAVA_HOME /usr/lib/jvm/java-1.6.0-openjdk-1.6.0.0.x86_64/jre
ENV APP_HOME /opt/collins
ENV LOG_HOME /var/log/collins

WORKDIR /build

# set up the JCE for runtime. To build this image, you need to grab the JCE zip from http://www.oracle.com/technetwork/java/javase/downloads/jce-6-download-429243.html
ADD ./jce_policy-6.zip /build/jce_policy-6.zip
RUN unzip -q jce_policy-6.zip && cp /build/jce/*jar $JAVA_HOME/lib/security/ && rm -f jce_policy-6.zip

# get Play, Collins, build, and deploy it to /opt/collins
RUN git clone https://github.com/tumblr/collins.git /build/collins && \
    echo "Fetching Play 2.0.8" && \
    wget -q http://downloads.typesafe.com/play/2.0.8/play-2.0.8.zip -O /build/play-2.0.8.zip && \
    unzip -q ./play-2.0.8.zip && \
    cd /build/collins && \
    git rev-parse HEAD > VERSION && \
    echo "Building Collins $(cat VERSION)" && \
    java -version 2>&1 && \
    PLAY_CMD=/build/play-2.0.8/play ./scripts/package.sh && \
    unzip -q /build/collins/target/collins.zip -d /opt/ && \
    rm -rf /build && \
    chown -R collins /opt/collins

# and add in all the default configs we want in this build
# these are the things you ought to change when vendorizing
ADD ./conf/docker/validations.conf /opt/collins/conf/validations.conf
ADD ./conf/docker/authentication.conf /opt/collins/conf/authentication.conf
ADD ./conf/docker/database.conf /opt/collins/conf/database.conf
ADD ./conf/docker/production.conf /opt/collins/conf/production.conf
ADD ./conf/docker/users.conf /opt/collins/conf/users.conf
ADD ./conf/docker/profiles.yaml /opt/collins/conf/profiles.yaml
ADD ./conf/docker/permissions.yaml /opt/collins/conf/permissions.yaml
ADD ./conf/docker/logger.xml /opt/collins/conf/logger.xml
RUN chown -R collins /opt/collins

WORKDIR /opt/collins
USER collins
EXPOSE 9000
CMD /usr/bin/java -server \
      -Dconfig.file=$APP_HOME/conf/production.conf \
      -Dhttp.port=9000 \
      -Dlogger.file=$APP_HOME/conf/logger.xml \
      -Dnetworkaddress.cache.ttl=1 \
      -Dnetworkaddress.cache.negative.ttl=1 \
      -Dcom.sun.management.jmxremote \
      -Dcom.sun.management.jmxremote.port=3333 \
      -Dcom.sun.management.jmxremote.authenticate=false \
      -Dcom.sun.management.jmxremote.ssl=false \
      -XX:MaxPermSize=384m \
      -XX:+CMSClassUnloadingEnabled \
      -XX:+PrintGCDetails \
      -XX:+PrintGCTimeStamps \
      -XX:+PrintGCDateStamps \
      -XX:+PrintTenuringDistribution \
      -XX:+PrintHeapAtGC \
      -Xloggc:$LOG_HOME/gc.log \
      -XX:+UseGCLogFileRotation \
      -cp "$APP_HOME/lib/*" \
      play.core.server.NettyServer $APP_HOME

