FROM openjdk:8-jdk AS build
MAINTAINER Gabe Conradi <gabe@tumblr.com>

ENV ACTIVATOR_VERSION=1.3.7
WORKDIR /build
# install prereqs
RUN apt-get update && \
    apt-get install --no-install-recommends -y zip unzip wget && \
    wget -q http://downloads.typesafe.com/typesafe-activator/$ACTIVATOR_VERSION/typesafe-activator-$ACTIVATOR_VERSION-minimal.zip -O /build/activator.zip && \
    unzip -q ./activator.zip

WORKDIR /build/collins
# install the bare minimum necessary for a scala project to install deps
# redownload all the deps each time the source changes
COPY ./build.sbt /build/collins/build.sbt
COPY ./project/ /build/collins/project/
# just download and update dependencies before we copy in source
RUN /build/activator-$ACTIVATOR_VERSION-minimal/activator update

COPY . /build/collins
RUN PLAY_CMD=/build/activator-$ACTIVATOR_VERSION-minimal/activator FORCE_BUILD=true ./scripts/package.sh
# install build to /opt/collins
RUN unzip -q /build/collins/target/collins.zip -d /opt/

FROM openjdk:8-jre
MAINTAINER Gabe Conradi <gabe@tumblr.com>

# Solr cores should be stored in a volume, so we arent writing stuff to our rootfs
VOLUME /opt/collins/conf/solr/cores/collins/data

WORKDIR /opt/collins

# copy the built artifacts from the build container into our deploy
COPY --from=build /opt/collins/ /opt/collins/
# Add in all the default configs we want in this build so collins can run.
# You probably will want to override these configs in production
COPY conf/docker /opt/collins/conf/

RUN apt-get update && \
    apt-get install --no-install-recommends -y ipmitool && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# expose HTTP, JMX
EXPOSE 9000 3333
CMD ["/usr/bin/java","-server","-Dconfig.file=/opt/collins/conf/production.conf","-Dhttp.port=9000","-Dlogger.file=/opt/collins/conf/logger.xml","-Dnetworkaddress.cache.ttl=1","-Dnetworkaddress.cache.negative.ttl=1","-Dcom.sun.management.jmxremote","-Dcom.sun.management.jmxremote.port=3333","-Dcom.sun.management.jmxremote.authenticate=false","-Dcom.sun.management.jmxremote.ssl=false","-XX:MaxPermSize=384m","-XX:+CMSClassUnloadingEnabled","-XX:-UsePerfData","-cp","/opt/collins/lib/*","play.core.server.NettyServer","/opt/collins"]
