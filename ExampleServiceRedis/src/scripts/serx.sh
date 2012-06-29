#!/bin/sh
#
# serx init.d script.
#
# All java services require the same directory structure:
#   /usr/local/$APP_NAME
#   /var/log/$APP_NAME
#   /var/run/$APP_NAME

APP_NAME="serx"
ADMIN_PORT="9900"
JMX_PORT="3333"
VERSION="@VERSION@"
APP_HOME="/usr/local/$APP_NAME/current"
DAEMON="/usr/local/bin/daemon"
JAR_NAME="$APP_NAME""_2.9.1-$VERSION.jar"
MAX_OPEN_FILES=8192
SHUTDOWN_TIMEOUT=5

if [ -z "$STAGE" ]; then
  STAGE="production"
fi

HEAP_OPTS="-Xmx4096m -Xms4096m -XX:NewSize=768m"
GC_OPTS="-XX:+UseParallelOldGC -XX:+UseAdaptiveSizePolicy -XX:MaxGCPauseMillis=1000 -XX:GCTimeRatio=99"
GC_LOG_OPTS="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC"
GC_LOG="-Xloggc:/var/log/$APP_NAME/gc.log"
DEBUG_OPTS="-XX:+HeapDumpOnOutOfMemoryError -XX:ErrorFile=/var/log/$APP_NAME/java_error%p.log"
JMX_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=$JMX_PORT -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS="-server -Dstage=$STAGE $JMX_OPTS $GC_OPTS $GC_LOG_OPTS $GC_LOG $HEAP_OPTS $DEBUG_OPTS"

if [ -f "/usr/local/lib/libheapster.jnilib" ]; then
  HEAPSTER_DIR="/usr/local/lib"
else
  HEAPSTER_DIR="/usr/local/$APP_NAME/shared/heapster"
fi

if [ -d $HEAPSTER_DIR ]; then
  JAVA_OPTS="-Xbootclasspath/a:$HEAPSTER_DIR -agentpath:$HEAPSTER_DIR/libheapster.jnilib ${JAVA_OPTS}"
fi

pidfile="/var/run/$APP_NAME/$APP_NAME.pid"
daemon_pidfile="/var/run/$APP_NAME/$APP_NAME-daemon.pid"
daemon_args="--name $APP_NAME --pidfile $daemon_pidfile --core -U --chdir /"
daemon_start_args="--stdout=/var/log/$APP_NAME/stdout --stderr=/var/log/$APP_NAME/error"

function running() {
  $DAEMON $daemon_args --running
}

function find_java() {
  if [ ! -z "$JAVA_HOME" ]; then
    return
  fi
  for dir in /opt/jdk /System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home /usr/java/default; do
    if [ -x $dir/bin/java ]; then
      JAVA_HOME=$dir
      break
    fi
  done
}

find_java


case "$1" in
  start)
    echo -n "Starting $APP_NAME... "

    if [ ! -r $APP_HOME/$JAR_NAME ]; then
      echo "FAIL"
      echo "*** $APP_NAME jar missing: $APP_HOME/$JAR_NAME - not starting"
      exit 1
    fi
    if [ ! -x $JAVA_HOME/bin/java ]; then
      echo "FAIL"
      echo "*** $JAVA_HOME/bin/java doesn't exist -- check JAVA_HOME?"
      exit 1
    fi
    if running; then
      echo "already running."
      exit 0
    fi

    ulimit -c unlimited || echo -n " (no coredump)"
    ulimit -n $MAX_OPEN_FILES
    $DAEMON $daemon_args $daemon_start_args -- sh -c "echo "'$$'" > $pidfile; exec ${JAVA_HOME}/bin/java ${JAVA_OPTS} -jar ${APP_HOME}/${JAR_NAME}"
    tries=0
    while ! running; do
      tries=$((tries + 1))
      if [ $tries -ge 5 ]; then
        echo "FAIL"
        exit 1
      fi
      sleep 1
    done
    echo "done."
  ;;

  stop)
    echo -n "Stopping $APP_NAME... "
    if ! running; then
      echo "wasn't running."
      exit 0
    fi

    curl -m ${SHUTDOWN_TIMEOUT} -s http://localhost:${ADMIN_PORT}/shutdown.txt > /dev/null
    tries=0
    while running; do
      tries=$((tries + 1))
      if [ $tries -ge 15 ]; then
        echo "FAILED SOFT SHUTDOWN, TRYING HARDER"
        if [ -f $pidfile ]; then
          kill $(cat $pidfile)
        else
          echo "CAN'T FIND PID, TRY KILL MANUALLY"
          exit 1
        fi
        hardtries=0
        while running; do
          hardtries=$((hardtries + 1))
          if [ $hardtries -ge 5 ]; then
            echo "FAILED HARD SHUTDOWN, TRY KILL -9 MANUALLY"
            exit 1
          fi
          sleep 1
        done
      fi
      sleep 1
    done
    echo "done."
  ;;

  status)
    if running; then
      echo "$APP_NAME is running."
    else
      echo "$APP_NAME is NOT running."
    fi
  ;;

  restart)
    $0 stop
    sleep 2
    $0 start
  ;;

  *)
    echo "Usage: ${APP_HOME}/scripts/${APP_NAME}.sh {start|stop|restart|status}"
    exit 1
  ;;
esac

exit 0
