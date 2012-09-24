#! /usr/bin/env sh
#
# collins - groovy kind of love
#
# chkconfig:   35 95 5
# description: groovy kind of love

APP_NAME="collins"
APP_HOME="/usr/local/$APP_NAME/current"
DAEMON="/usr/local/bin/daemon"
LISTEN_PORT=8080
FILE_LIMIT=8192
COLLINS_USER="collins"
HEAP_OPTS="-XX:MaxPermSize=256m"

# Check for config overrides
[ -f /etc/sysconfig/collins ] && . /etc/sysconfig/collins

APP_OPTS="-Dconfig.file=$APP_HOME/conf/production.conf -Dhttp.port=${LISTEN_PORT} -Dlogger.file=$APP_HOME/conf/logger.xml"
DNS_OPTS="-Dnetworkaddress.cache.ttl=1 -Dnetworkaddress.cache.negative.ttl=1"
GC_OPTS="-XX:+CMSClassUnloadingEnabled"
GC_LOG_OPTS="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC"
GC_LOG="-Xloggc:/var/log/$APP_NAME/gc.log"
DEBUG_OPTS="-XX:ErrorFile=/var/log/$APP_NAME/java_error%p.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/collinsDump.hprof"
JMX_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=3333 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
JAVA_OPTS="-server $APP_OPTS $DNS_OPTS $JMX_OPTS $GC_OPTS $GC_LOG_OPTS $GC_LOG $HEAP_OPTS $DEBUG_OPTS"

pidfile="/var/run/$APP_NAME/$APP_NAME.pid"
daemon_pidfile="/var/run/$APP_NAME/$APP_NAME-daemon.pid"
daemon_args="-u $COLLINS_USER --env HOME=$APP_HOME --name $APP_NAME --pidfile $daemon_pidfile --core -U --chdir /"
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
  initdb)
    echo "mysql root password. Enter for none."
    mysql -u root -p -e 'create database if not exists collins;'
    if [ -z "$2" ]; then
      read -p "Application Database Username: " db_username
    else
      db_username=$2;
    fi
    if [ -z "$3" ]; then
      stty -echo
      read -p "Application Database Password: " db_password; echo
      stty echo
    else
      db_password=$3;
    fi
    echo "mysql root password. Enter for none."
    mysql -u root -p -e "grant all privileges on collins.* to $db_username@'127.0.0.1' identified by '$db_password';"
    if [ ! -x $JAVA_HOME/bin/java ]; then
      echo "FAIL"
      echo "Didn't find $JAVA_HOME/bin/java, check JAVA_HOME?"
      exit 1
    fi
    echo "Running migrations"
    ${JAVA_HOME}/bin/java ${APP_OPTS} -cp "$APP_HOME/lib/*" DbUtil $APP_HOME/conf/evolutions/
    echo "Database initialization attempted" > /var/run/$APP_NAME/install.log
  ;;

  start)
    echo -n "Starting $APP_NAME... "

    if [ ! -r $APP_HOME/lib/$APP_NAME* ]; then
      echo "FAIL"
      echo "*** $APP_NAME jar missing: $APP_HOME/lib/$APP_NAME - not starting"
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
    ulimit -n $FILE_LIMIT || echo -n " (could not set file limit)"
    $DAEMON $daemon_args $daemon_start_args -- sh -c "echo "'$$'" > $pidfile; exec ${JAVA_HOME}/bin/java ${JAVA_OPTS} -cp ${APP_HOME}'/lib/*' play.core.server.NettyServer ${APP_HOME}"
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

    kill $(cat $pidfile)
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
      exit 3
    fi
  ;;

  restart)
    $0 stop
    sleep 2
    $0 start
  ;;

  *)
    echo "Usage: /etc/init.d/${APP_NAME}.sh {start|stop|restart|status|initdb}"
    echo "Note: initdb can optionally be passed a username followed by a password to initialize the db"
    exit 1
  ;;
esac

exit 0

