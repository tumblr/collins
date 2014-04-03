# Docker

Docker is an open-source project to easily create lightweight, portable,
self-sufficient containers from any application. The same container that a
developer builds and tests on a laptop can run at scale, in production, on VMs,
bare metal, OpenStack clusters, public clouds and more. For more infos, see
[docker.io](http://www.docker.io/).

# Running collins on Docker

There is a trusted build of collins available, maintained by [fish](https://index.docker.io/u/fish).

The bare image isn't very useful on it's own, but you can easily vendorize it by
creating custom configs in `conf/` and create a custom Dockerfile using the
collins build as base:

    FROM fish/collins
    ADD conf /collins/conf/

Build this by running:

    $ docker build -t my-vendored-collins .

Next is the database, let's assume you want to use a mysq. Start a mysqld
container by running:

    $ sudo docker run -name="mysql-server" -p 3306 -d jonwood/mysql-server


Now create collins database and user:

    $ mysql -u root -h 127.0.0.1 -P $(sudo docker port mysql-server 3306 | cut -d: -f2) -e \
      "create database if not exists collins;
       grant all privileges on collins.* to collins@'%' identified by 'collins123'"

And initialized the database:

    $ sudo docker run -link mysql-server:db collins \
      -Ddb.collins.password="collins123" \
      DbUtil conf/evolutions

Then run collins:

    $ sudo docker run -link mysql-server:db -name="collins-server" -p 9000 \
      collins -Ddb.collins.password="collins123" play.core.server.NettyServer

(If you're setting the db password in the production.conf, you can ommit
play.core.server.NettyServer)


If you start a new container based on existing data in mysql, you need to update
the solr index:

    $ curl --basic -u <user>:<password> \
      http://localhost:$(sudo docker port collins-server 9000|cut -d: -f2)/api/admin/solr

# Build collins on Docker

If you want to build the docker image on your own, this in `contrib/docker`:

    $ docker build -t collins .


