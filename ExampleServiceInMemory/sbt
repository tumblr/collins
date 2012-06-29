#!/bin/sh

if [ -z "$SBT_OPTS" ]; then
	SBT_OPTS="-Xmx4096m -Xms4096m -XX:NewSize=768m -XX:MaxPermSize=1024m";
fi
if [ -z "$TUMBLR_LOCAL" ]; then
	TUMBLR_LOCAL="true"
fi
if [ -n "$TUMBLR_LOCAL" ]; then
        if [ -z "$TUMBLR_REPO" ]; then
                export TUMBLR_REPO="http://repo.tumblr.net:8081/nexus/content/"
                if [ -z "$SBT_BOOT_PROPERTIES" ]; then
	                SBT_BOOT_PROPERTIES="-Dsbt.boot.properties=`dirname $0`/project/sbt.boot.properties"
                fi
        fi
fi
if [ -z "$SBT_BOOT_PROPERTIES" ]; then
  SBT_BOOT_PROPERTIES=""
fi
java ${SBT_OPTS} ${SBT_BOOT_PROPERTIES} -Dactors.minPoolSize=128 -Dactors.corePoolSize=256 -Dactors.maxPoolSize=512 -jar `dirname $0`/lib_unmanaged/sbt-launch.jar "$@"
