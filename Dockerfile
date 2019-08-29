ARG activator_version=1.3.7

FROM openjdk:8-jdk AS dev

ARG activator_version
WORKDIR /build
# install prereqs
RUN wget -q http://downloads.typesafe.com/typesafe-activator/${activator_version}/typesafe-activator-${activator_version}-minimal.zip -O /build/activator.zip && \
    unzip -q ./activator.zip

WORKDIR /build/collins
# install the bare minimum necessary for a scala project to install deps
# redownload all the deps each time the source changes
COPY ./build.sbt /build/collins/build.sbt
COPY ./project/ /build/collins/project/
# just download and update dependencies before we copy in source
RUN /build/activator-${activator_version}-minimal/activator update
ENTRYPOINT /build/activator-${activator_version}-minimal/activator

# Build stage to build and package the app
FROM openjdk:8-jdk AS build

ARG activator_version
WORKDIR /build/collins

RUN apt-get update && apt-get install --no-install-recommends zip

# Copy activator from dev stage
COPY --from=dev /build /build

# Copy source and build package
COPY . /build/collins
RUN PLAY_CMD=/build/activator-${activator_version}-minimal/activator ./scripts/package.sh

# Production stage with just the build artifacts and configs
FROM openjdk:8-jre AS production

# Solr cores should be stored in a volume, so we arent writing stuff to our rootfs
VOLUME /opt/collins/conf/solr/cores/collins/data

WORKDIR /opt/collins

# copy the built artifacts from the build container and install to /opt/collins
COPY --from=build /build/collins/target/collins.zip /build/collins/target/collins.zip
RUN unzip -q /build/collins/target/collins.zip -d /opt/
# Add in all the default configs we want in this build so collins can run.
# You probably will want to override these configs in production
COPY conf/docker /opt/collins/conf/

RUN apt-get update && \
    apt-get install --no-install-recommends -y ipmitool && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# expose HTTP, JMX
EXPOSE 9000 3333
CMD ["java","-server","-Dconfig.file=/opt/collins/conf/production.conf","-Dhttp.port=9000","-Dlogger.file=/opt/collins/conf/logger.xml","-Dnetworkaddress.cache.ttl=1","-Dnetworkaddress.cache.negative.ttl=1","-Dcom.sun.management.jmxremote","-Dcom.sun.management.jmxremote.port=3333","-Dcom.sun.management.jmxremote.authenticate=false","-Dcom.sun.management.jmxremote.ssl=false","-XX:MaxPermSize=384m","-XX:+CMSClassUnloadingEnabled","-XX:-UsePerfData","-cp","/opt/collins/lib/*","play.core.server.NettyServer","/opt/collins"]
