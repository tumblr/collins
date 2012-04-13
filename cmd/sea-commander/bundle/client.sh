#!/bin/sh
java -cp zookeeper-3.3.5.jar:log4j-1.2.15.jar:conf:jline-0.9.94.jar \
	org.apache.zookeeper.ZooKeeperMain -server 127.0.0.1:2181
