#!/bin/bash

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
two-nic"
lldp_files=($LldpFiles)
num_lldp_files=${#lldp_files[*]}

TAG=$1;
if [ -z "$TAG" ]; then
	echo "No tag specifid"
	exit
fi
DO_CREATE="$2"

if [ -n "$DO_CREATE" ]; then
  curl --basic -X PUT -H "Accept: text/plain" -u blake:admin:first "${URL}/${TAG}"
fi

LSHW_FILE="./test/resources/lshw-${lshw_files[$((RANDOM%num_lshw_files))]}.xml"
LLDP_FILE="./test/resources/lldpctl-${lldp_files[$((RANDOM%num_lldp_files))]}.xml"
curl --basic \
  -H "Accept: text/plain" \
  -u blake:admin:first \
  --data-urlencode "lldp@${LLDP_FILE}" \
  --data-urlencode "lshw@${LSHW_FILE}" \
  --data-urlencode 'CHASSIS_TAG=Testing this' "${URL}/${TAG}"

curl --basic \
  -H "Accept: text/plain" \
  -u blake:admin:first \
  -d RACK_POSITION=pos1 \
  -d POWER_PORT_A=portA \
  -d POWER_PORT_B=portB \
  -d POWER_OUTLET_A=outletA \
  -d POWER_OUTLET_B=outletB "${URL}/${TAG}"
