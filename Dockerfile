FROM java:7
MAINTAINER Gabe Conradi <gabe@tumblr.com>

RUN apt-get update && apt-get install -y zip unzip && rm -r /var/lib/apt/lists/*

RUN useradd -Ur -d /opt/collins collins
RUN for dir in /build /build/collins /var/log/collins /var/run/collins; do mkdir $dir; chown collins $dir; done
ENV APP_HOME /opt/collins
ENV LOG_HOME /var/log/collins

WORKDIR /build
# get Play, Collins, build, and deploy it to /opt/collins
COPY . /build/collins
RUN echo "Fetching Play 2.0.8" && \
    wget -q http://downloads.typesafe.com/play/2.0.8/play-2.0.8.zip -O /build/play-2.0.8.zip && \
    unzip -q ./play-2.0.8.zip && \
    cd collins && \
    java -version 2>&1 && \
    PLAY_CMD=/build/play-2.0.8/play ./scripts/package.sh && \
    unzip -q /build/collins/target/collins.zip -d /opt/ && \
    cd / && rm -rf /build && \
    chown -R collins /opt/collins

# Add in all the default configs we want in this build so collins can run.
# Override /opt/collins/conf with your own configs with -v
COPY conf/docker/validations.conf     /opt/collins/conf/validations.conf
COPY conf/docker/authentication.conf  /opt/collins/conf/authentication.conf
COPY conf/docker/database.conf        /opt/collins/conf/database.conf
COPY conf/docker/production.conf      /opt/collins/conf/production.conf
COPY conf/docker/users.conf           /opt/collins/conf/users.conf
COPY conf/docker/profiles.yaml        /opt/collins/conf/profiles.yaml
COPY conf/docker/permissions.yaml     /opt/collins/conf/permissions.yaml
COPY conf/docker/logger.xml           /opt/collins/conf/logger.xml

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

