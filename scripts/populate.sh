#!/bin/bash

URL="http://localhost:9000/api/asset"
LshwFiles="10g
amd-opteron-wonky
b0214
b0216
dell-r620-single-cpu
lvm
intel
new-web-old-lshw
old-web
old
quad
small
virident"
lshw_files=($LshwFiles)
num_lshw_files=${#lshw_files[*]}

LldpFiles="single
four-nic
two-nic"
lldp_files=($LldpFiles)
num_lldp_files=${#lldp_files[*]}

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
RESOURCE_DIR="${DIR}/../test/resources/"

for i in `seq 300 310`; do
  TAG="tumblrtag${i}"
  LSHW_FILE="${RESOURCE_DIR}/lshw-${lshw_files[$((RANDOM%num_lshw_files))]}.xml"
  LLDP_FILE="${RESOURCE_DIR}/lldpctl-${lldp_files[$((RANDOM%num_lldp_files))]}.xml"
  curl --basic -X PUT -H "Accept: text/plain" -u blake:admin:first "${URL}/${TAG}"
  curl --basic -H "Accept: text/plain" -u blake:admin:first --data-urlencode "lldp@${LLDP_FILE}" --data-urlencode "lshw@${LSHW_FILE}" --data-urlencode 'CHASSIS_TAG=Testing this' "${URL}/${TAG}"
  sleep 10
done
