#!/bin/bash
# collins - groovy kind of love
# http://tumblr.github.io/collins/#about
#
# chkconfig:   35 95 5
# description: Collins inventory asset manager
##

# use LSB init functions
[ -r /lib/lsb/init-functions ] && . /lib/lsb/init-functions || (echo "LSB init functions are required!" && exit 2)


APP_NAME="collins"
APP_HOME="/usr/local/$APP_NAME/current"
LOG_HOME='/var/log'
LISTEN_PORT=8080
FILE_LIMIT=8192
COLLINS_USER="collins"

##
# Defaults for JVM
# Optionally tweak these via sysconfig
##
HEAP_OPTS=""
# Play/Scala dynamic class allocation uses a lot of space
PERMGEN_OPTS="-XX:MaxPermSize=384m -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC"
# http://blog.ragozin.info/2011/09/hotspot-jvm-garbage-collection-options.html
# http://www.javaworld.com/javaworld/jw-01-2002/jw-0111-hotspotgc.html
GC_LOGGING_OPTS="-XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps -XX:+PrintTenuringDistribution -XX:+PrintHeapAtGC"
GC_LOG="-Xloggc:${LOG_HOME}/$APP_NAME/gc.log -XX:+UseGCLogFileRotation"
JMX_OPTS="-Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=3333 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"
DEBUG_OPTS="-XX:ErrorFile=${LOG_HOME}/$APP_NAME/java_error%p.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/collinsDump.hprof"

# Check for config overrides
[ -f /etc/sysconfig/collins ] && . /etc/sysconfig/collins

APP_OPTS="-Dconfig.file=$APP_HOME/conf/production.conf -Dhttp.port=${LISTEN_PORT} -Dlogger.file=$APP_HOME/conf/logger.xml"
DNS_OPTS="-Dnetworkaddress.cache.ttl=1 -Dnetworkaddress.cache.negative.ttl=1"
JAVA_OPTS="-server $APP_OPTS $DNS_OPTS $JMX_OPTS $PERMGEN_OPTS $GC_LOGGING_OPTS $GC_LOG $HEAP_OPTS $DEBUG_OPTS"

pidfile="/var/run/$APP_NAME/$APP_NAME.pid"

function running() {
  [[ ! -s $pidfile ]] && return 1
  ps -fp $(cat $pidfile) &>/dev/null
}

function ensure_java_binary() {
  local executable="${1:-$JAVA_HOME/bin/java}"
  if [[ ! -x $executable ]]; then
    log_failure_msg "Check $executable exists"
    echo "*** $executable isn't executable or doesn't exist -- check JAVA_HOME?"
    exit 1
  fi
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

initialize_db() {
  declare db_username="$1";
  declare db_password="$2";

  echo "Initializing collins database on localhost..."

  if [ -z "$db_username" ]; then
    read -p "Collins Database Username: " db_username
  else
    db_username="$2";
  fi
  if [ -z "$db_password" ]; then
    stty -echo
    read -p "Collins Database Password: " db_password; echo
    stty echo
  else
    db_password="$3";
  fi

  echo "Please enter mysql root password. Press <enter> for none."
  mysql -u root -p -e 'CREATE DATABASE IF NOT EXISTS collins;'

  echo "Granting privs to collins user on localhost..."
  echo "Please enter mysql root password. Press <enter> for none."
  mysql -u root -p -e "GRANT ALL PRIVILEGES ON collins.* to $db_username@'127.0.0.1' IDENTIFIED BY '$db_password';"
}

evolve_db() {
    ensure_java_binary
    echo -n "Running migrations"
    ${JAVA_HOME}/bin/java ${APP_OPTS} -cp "$APP_HOME/lib/*" DbUtil $APP_HOME/conf/evolutions/
    [[ $? -eq 0 ]] && log_success_msg || log_failure_msg
    echo "Database initialization attempted" >> /var/run/$APP_NAME/install.log
}

case "$1" in
  initdb)
    initialize_db "$2" "$3"
    evolve_db
  ;;

  evolvedb)
    evolve_db
  ;;

  start)
    ensure_java_binary

    if [ ! -r $APP_HOME/lib/$APP_NAME* ]; then
      log_failure_msg "Finding $APP_HOME/lib/$APP_NAME jar"
      echo "*** $APP_NAME jar missing: $APP_HOME/lib/$APP_NAME - not starting"
      exit 1
    fi

    if running; then
      log_warning_msg "Check if collins is not already running"
      echo "Skipping, $APP_NAME is already running"
      exit 0
    fi

    # if we are already running as $COLLINS_USER, no need to su
    # This lets us run collins.sh as the app user, for example inside a docker container
    java_command="${JAVA_HOME}/bin/java ${JAVA_OPTS} -cp ${APP_HOME}/lib/\* play.core.server.NettyServer ${APP_HOME}"
    if [[ $(whoami) = $COLLINS_USER ]] ; then
      start_command="$java_command"
    else
      start_command="su -s /bin/bash -c '$java_command' $COLLINS_USER"
    fi

    ulimit -c unlimited || log_warning_msg "Unable to set core ulimit to unlimited"
    ulimit -n $FILE_LIMIT || log_warning_msg "Unable to set nofiles ulimit to $FILE_LIMIT"
    echo -n "Starting $APP_NAME... "
    nohup bash -c "$start_command" >>${LOG_HOME}/$APP_NAME/stdout 2>>${LOG_HOME}/$APP_NAME/error </dev/null &
    echo $! >$pidfile
    # lets chill for a sec before checking its up
    sleep 3s
    tries=0
    if ! running ; then
      log_failure_msg
      echo "*** Try checking the logs in ${LOG_HOME}/$APP_NAME/{stdout,error} to see what the haps are"
      rm -f $pidfile
      exit 1
    fi
    log_success_msg
  ;;

  stop)
    if ! running; then
      log_failure_msg "$APP_NAME is not running"
      exit 0
    fi

    echo -n "Stopping $APP_NAME... "
    kill $(cat $pidfile) &>/dev/null
    tries=0
    while running; do
      tries=$((tries + 1))
      if [ $tries -ge 15 ]; then
        # kill didnt take after 15s, lets try again
        if [ -f $pidfile ]; then
          kill $(cat $pidfile) &>/dev/null
        else
          log_failure_msg
          echo "Unable to find pid, try killing the process manually"
          exit 1
        fi
        hardtries=0
        # wait another 5 seconds to see if it stopped after the 2nd kill
        while running; do
          hardtries=$((hardtries + 1))
          if [ $hardtries -ge 5 ]; then
            log_failure_msg
            echo "Unable to stop $APP_NAME, try 'kill -9 $(cat $pidfile)'"
            exit 1
          fi
          sleep 1
        done
      fi
      sleep 1
    done
    log_success_msg
  ;;

  status)
    if running; then
      echo "$APP_NAME (pid $(cat $pidfile)) is running."
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
    echo "Usage: $0 {start|stop|restart|status|initdb|evolvedb}"
    echo "Note: 'initdb' can optionally be passed a username followed by a password to initialize the db"
    exit 1
  ;;
esac

exit 0

