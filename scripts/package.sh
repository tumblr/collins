#!/usr/bin/env bash

set -e

if [ -z "$1" ]; then
  DEBUG=0
else
  set -x
  DEBUG=1
fi

if [ -z "$PLAY_CMD" ]; then
  PLAY_CMD="$HOME/src/play-2.2.6/play";
fi

if [ ! -f $PLAY_CMD ]; then
  echo "unable to find play command @ $PLAY_CMD"
  exit 1
fi

if [ $DEBUG -eq 0 ]; then
  rm -rf target/universal
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

cp universal/stage/lib/* collins/lib

# Copy over test data for use with populate.sh
mkdir -p collins/test/resources
cp ../test/resources/*.xml collins/test/resources
cp ../test/resources/*.yaml collins/test/resources

# Copy over scripts
for script in collins.sh package.sh populate.sh setup; do
  cp ../scripts/${script} collins/scripts/${script}
done

# Copy over configs
for conf in logger.xml permissions.yaml validations.conf; do
  cp ../conf/${conf} $CONF_DIR/${conf}
done
cp ../conf/production_starter.conf $CONF_DIR/production.conf

cp ../conf/solr/solr.xml $CONF_DIR/solr/
cp -R ../conf/solr/conf/* $CONF_DIR/solr/conf/
cp -R ../conf/evolutions/* $CONF_DIR/evolutions/
zip -r collins.zip collins
