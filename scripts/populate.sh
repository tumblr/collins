#!/bin/sh

URL="http://localhost:8080/api/asset"
LshwFiles="10g
old-web
basic
intel
old
virident"
lshw_files=($LshwFiles)
num_lshw_files=${#lshw_files[*]}

LldpFiles="single
four-nic
two-nic"
lldp_files=($LldpFiles)
num_lldp_files=${#lldp_files[*]}

for i in `seq 300 310`; do
  TAG="tumblrtag${i}"
  LSHW_FILE="./test/resources/lshw-${lshw_files[$((RANDOM%num_lshw_files))]}.xml"
  LLDP_FILE="./test/resources/lldpctl-${lldp_files[$((RANDOM%num_lldp_files))]}.xml"
  curl --basic -X PUT -H "Accept: text/plain" -u blake:admin:first "${URL}/${TAG}"
  curl --basic -H "Accept: text/plain" -u blake:admin:first --data-urlencode "lldp@${LLDP_FILE}" --data-urlencode "lshw@${LSHW_FILE}" --data-urlencode 'CHASSIS_TAG=Testing this' "${URL}/${TAG}"
  sleep 10
done
