#!/bin/sh
java -cp zookeeper-3.3.5.jar:log4j-1.2.15.jar:conf \
	org.apache.zookeeper.server.quorum.QuorumPeerMain \
	zoo-multiple.cfg
