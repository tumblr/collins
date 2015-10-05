FROM java:7
MAINTAINER Gabe Conradi <gabe@tumblr.com>

RUN apt-get update && apt-get install -y zip unzip && rm -r /var/lib/apt/lists/*

RUN useradd -Ur -d /opt/collins collins
RUN for dir in /build /build/collins /var/log/collins /var/run/collins; do mkdir $dir; chown collins $dir; done

WORKDIR /build
# get Play, Collins, build, and deploy it to /opt/collins
COPY . /build/collins
RUN wget -q http://downloads.typesafe.com/typesafe-activator/1.3.6/typesafe-activator-1.3.6-minimal.zip -O /build/typesafe-activator-1.3.6-minimal.zip && \
    unzip -q ./typesafe-activator-1.3.6-minimal.zip && \
    cd collins && \
    java -version 2>&1 && \
    PLAY_CMD=/build/activator-1.3.6-minimal/activator ./scripts/package.sh && \
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
EXPOSE 3333
CMD ["/usr/bin/java","-server","-Dconfig.file=/opt/collins/conf/production.conf","-Dhttp.port=9000","-Dlogger.file=/opt/collins/conf/logger.xml","-Dnetworkaddress.cache.ttl=1","-Dnetworkaddress.cache.negative.ttl=1","-Dcom.sun.management.jmxremote","-Dcom.sun.management.jmxremote.port=3333","-Dcom.sun.management.jmxremote.authenticate=false","-Dcom.sun.management.jmxremote.ssl=false","-XX:MaxPermSize=384m","-XX:+CMSClassUnloadingEnabled","-XX:+PrintGCDetails","-XX:+PrintGCTimeStamps","-XX:+PrintGCDateStamps","-XX:+PrintTenuringDistribution","-XX:+PrintHeapAtGC","-Xloggc:/var/log/collins/gc.log","-XX:+UseGCLogFileRotation","-cp","/opt/collins/lib/*","play.core.server.NettyServer","/opt/collins"]

