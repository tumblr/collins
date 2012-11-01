#! /usr/bin/env sh

if [ -z "$1" ]; then
  DEBUG=0
else
  set -x
  DEBUG=1
fi

if [ -z "$PLAY_CMD" ]; then
  PLAY_CMD="$HOME/src/play-2.0.3/play";
fi

if [ $DEBUG -eq 0 ]; then
  rm -rf targed/staged
  $PLAY_CMD clean compile stage
fi

cd target

CONF_DIR="collins/conf"

rm -rf collins
for dir in collins collins/lib collins/scripts $CONF_DIR; do
  mkdir $dir
done
for dir in $CONF_DIR/solr/conf $CONF_DIR/solr/data; do
  mkdir -p $dir
done

cp staged/* collins/lib

cp ../scripts/collins.sh collins/scripts/collins.sh
cp ../conf/logger.xml $CONF_DIR
cp ../conf/production*.conf $CONF_DIR
cp ../conf/permissions.yaml $CONF_DIR
cp ../conf/validations.conf $CONF_DIR

cp -R ../conf/solr/conf/* $CONF_DIR/solr/conf/
cp -R ../conf/evolutions/* $CONF_DIR/evolutions/
zip -r collins.zip collins
