#! /usr/bin/env sh

if [ -z "$PLAY_CMD" ]; then
  PLAY_CMD="$HOME/src/Play20/play";
fi

$PLAY_CMD clean compile stage
cd target

mkdir collins
mkdir collins/scripts
mv staged collins/lib
cp ../scripts/collins.sh collins/scripts/collins.sh
cp ../conf/production.conf collins/
cp -R ../db collins/scripts/
zip -r collins.zip collins
