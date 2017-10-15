FROM openjdk:8-jre

# Solr cores should be stored in a volume, so we arent writing stuff to our rootfs
VOLUME /opt/collins/conf/solr/cores/collins/data

COPY target/collins.zip /opt/collins.zip
RUN unzip /opt/collins.zip -d /opt && rm /opt/collins.zip

WORKDIR /opt/collins
COPY conf/docker conf

EXPOSE 9000 3333
CMD ["/usr/bin/java","-server","-Dconfig.file=/opt/collins/conf/production.conf","-Dhttp.port=9000","-Dlogger.file=/opt/collins/conf/logger.xml","-Dnetworkaddress.cache.ttl=1","-Dnetworkaddress.cache.negative.ttl=1","-Dcom.sun.management.jmxremote","-Dcom.sun.management.jmxremote.port=3333","-Dcom.sun.management.jmxremote.authenticate=false","-Dcom.sun.management.jmxremote.ssl=false","-XX:MaxPermSize=384m","-XX:+CMSClassUnloadingEnabled","-XX:-UsePerfData","-cp","/opt/collins/lib/*","play.core.server.NettyServer","/opt/collins"]

