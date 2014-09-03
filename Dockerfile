FROM centos:centos6
MAINTAINER Gabe Conradi <gabe@tumblr.com>
#NOTE: you should use vendorize this container by deploying your own production.conf to /opt/collins/conf/production.conf

ADD http://mirror.sfo12.us.leaseweb.net/epel/6/i386/epel-release-6-8.noarch.rpm /tmp/epel.rpm
RUN rpm -ivh /tmp/epel.rpm
RUN yum install -y wget zip unzip git daemonize java-1.6.0-openjdk java-1.6.0-openjdk-devel
RUN useradd -Ur -d /opt/collins collins
RUN for dir in /build /var/log/collins /var/run/collins; do mkdir $dir; chown collins $dir; done
ENV JAVA_HOME /usr/lib/jvm/java-1.6.0-openjdk-1.6.0.0.x86_64/jre

WORKDIR /build
ADD http://downloads.typesafe.com/play/2.0.8/play-2.0.8.zip /build/play-2.0.8.zip
RUN unzip -q ./play-2.0.8.zip
# clone collins tip into /build (we should use tagged releases instead here)
RUN git clone https://github.com/tumblr/collins.git /build/collins

# set up the JCE. In order to build this image, you need to grab the JCE zip from http://www.oracle.com/technetwork/java/javase/downloads/jce-6-download-429243.html
ADD ./jce_policy-6.zip /build/jce_policy-6.zip
RUN unzip -q jce_policy-6.zip && cp /build/jce/*jar $JAVA_HOME/lib/security/

WORKDIR /build/collins
# lets make a note of what sha we are running
RUN git rev-parse HEAD > VERSION && echo "Building Collins $(cat VERSION)" && java -version
RUN PLAY_CMD=../play-2.0.8/play ./scripts/package.sh

# now lets deploy this build into /opt/collins
WORKDIR /opt
RUN cp /build/collins/target/collins.zip ./ && unzip -q collins.zip
RUN rm -rf /build

# set up some default options for this environment
# And turn off the custom GC algorithm that isnt supported in JDK6
RUN echo -e 'APP_HOME=/opt/collins\nLISTEN_PORT=9000\nCOLLINS_USER=collins\nPERMGEN_OPTS="-XX:MaxPermSize=384m -XX:+CMSClassUnloadingEnabled"' > /etc/sysconfig/collins

USER collins
EXPOSE 8080
CMD [ "/opt/collins/scripts/collins.sh", "start" ]

