#!/bin/sh

URL="http://localhost:9000/api/asset"
Files="10g
basic
intel
old
virident"
files=($Files)
num_files=${#files[*]}

for i in `seq 2 25`; do
  TAG="tumblrtag${i}"
  FILE="./test/resources/lshw-${files[$((RANDOM%num_files))]}.xml"
  curl --basic -X PUT -H "Accept: text/plain" -u blake:admin:first "${URL}/${TAG}"
  curl --basic -H "Accept: text/plain" -u blake:admin:first --data-urlencode "lshw@${FILE}" --data-urlencode 'chassis_tag=Testing this' "${URL}/${TAG}"
done
