#!/bin/bash 

root=$(
  cd $(dirname $0)
  /bin/pwd
)

dir=$1

cd $dir
git add .
git commit -am"publish by $USER"
git push origin gh-pages
