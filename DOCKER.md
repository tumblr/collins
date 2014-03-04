# Docker

Docker is an open-source project to easily create lightweight, portable,
self-sufficient containers from any application. The same container that a
developer builds and tests on a laptop can run at scale, in production, on VMs,
bare metal, OpenStack clusters, public clouds and more. For more infos, see
[docker.io](http://www.docker.io/).

This document explains how to build and run collins as a docker container, though it is possible to build and run collins without Docker if you prefer.

# Running collins on docker:

Create conf/production.conf:

    $ alias rand="head /dev/urandom | shasum -a 512 | fold -w64 | head -n1"
    $ MYSQL_PASS=$(rand)
    $ sed "s/^db.collins.user=.*/db.collins.user=\"collins\"/;s/^db.collins.password=.*/db.collins.password=\"$MYSQL_PASS\"/;s/^collins_conf_dir.*/collins_conf_dir=\"conf\/\"/;s/\(application.secret=\"\).*/\1$(rand)\"/;s/\(crypto.key=\"\).*/\1$(rand)\"/" \
      conf/production_starter.conf > conf/production.conf

Feel free to edit other options, but you can pass any configuration options via
the command line.

You should also edit `conf/users.conf` and `conf/permissions.yaml`.
See collins documentation for help. Otherwise the default admin credentials are:

- user: blake
- password: admin:first


Now build the docker image by running:

    $ docker build -t collins .


Start a mysqld container:

    $ sudo docker run -name="mysql-server" -p 3306 -d jonwood/mysql-server


Create collins database and user:

    $ echo $MYSQL_PASS
    $ mysql -u root -h 127.0.0.1 -P $(sudo docker port mysql-server 3306 | cut -d: -f2) -e \
      "create database if not exists collins;
       grant all privileges on collins.* to collins@'%' identified by '< paste mysql password here >'"

Initialized the database:

    $ sudo docker run -link mysql-server:db collins \
      DbUtil conf/evolutions

Run collins:

    $ sudo docker run -link mysql-server:db -name="collins-server" -p 9000 collins

If you start a new container based on existing data in mysql, you need to update
the solr index:

    $ curl --basic -u <user>:<password> \
      http://localhost:$(sudo docker port collins-server 9000|cut -d: -f2)/api/admin/solr

