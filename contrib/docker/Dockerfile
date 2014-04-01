FROM ubuntu
MAINTAINER Johannes 'fish' Ziemke <fish@docker.com>

WORKDIR /collins

RUN apt-get update

# Disable upstart
RUN dpkg-divert --local --rename --add /sbin/initctl

# We can't mknod, so fake it
RUN apt-get -y -q install fakeroot && fakeroot apt-get -y -q install fuse

RUN apt-get install -y -q wget unzip default-jre default-jdk git

RUN wget http://download.playframework.org/releases/play-2.0.4.zip && unzip play-2.0.4.zip
RUN for dir in /collins /var/log/collins /var/run/collins; do mkdir $dir; chown nobody $dir; done
ADD RUN git clone https://github.com/tumblr/collins.git /collins

RUN ./play-2.0.4/play compile stage

EXPOSE 9000
RUN printf '#!/bin/sh\nexec java -cp "target/staged/*" -Dpidfile.path=/dev/null -Ddb.collins.url="jdbc:$(echo $DB_PORT|sed 's/^tcp/mysql/')/collins?autoReconnect=true&interactiveClient=true" $@' > run.sh && chmod a+x run.sh
ENTRYPOINT [ "./run.sh" ]
CMD [ "play.core.server.NettyServer" ]
