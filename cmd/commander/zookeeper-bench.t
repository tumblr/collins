{{/* global arguments */}}

{{ define "global" }}
{
	"hosts": [ "10.60.29.155", "10.60.25.184", "10.60.24.250", "10.60.24.66" ]
}
{{ end }}

{{/* commands */}}

{{ define "start" }}
[
{ "script": "start-zookeeper", "host": "10.60.29.155", "args": { "ServerID": "1" } },
{ "script": "start-zookeeper", "host": "10.60.25.184", "args": { "ServerID": "2" } },
{ "script": "start-zookeeper", "host": "10.60.24.250", "args": { "ServerID": "3" } },
{ "script": "start-zookeeper", "host": "10.60.24.66", "args": { "ServerID": "4" } }
]
{{ end }}

{{ define "stop" }}
[
{ "script": "stop-zookeeper", "host": "10.60.29.155", "args": { "ServerID": "1" } },
{ "script": "stop-zookeeper", "host": "10.60.25.184", "args": { "ServerID": "2" } },
{ "script": "stop-zookeeper", "host": "10.60.24.250", "args": { "ServerID": "3" } },
{ "script": "stop-zookeeper", "host": "10.60.24.66",  "args": { "ServerID": "4" } }
]
{{ end }}

{{ define "bench" }}
[
{ "script": "launch-zookeeper-bench", "host": "10.60.24.250", "args": { "Id": "a", "Twin": "b" } },
{ "script": "launch-zookeeper-bench", "host": "10.60.24.66", "args": { "Id": "b", "Twin": "a" } }
]
{{ end }}

{{/* scripts */}}

{{ define "launch-zookeeper-bench" }}
cd zookeeper.devbox
./zookeeper-bench -id {{.Id}} -twin {{.Twin}} -k 50 -n 100 -zk &
{{ end }}

{{ define "start-zookeeper" }}
cd zookeeper.devbox
mkdir -p var/zookeeper
rm -rf var/zookeeper/*
echo {{.ServerID}} > var/zookeeper/myid
./start-multiple.sh &
{{ end }}

{{ define "stop-zookeeper" }}
kill `ps ax | grep zookeeper | grep java | awk '{ print $1 }'`
{{ end }}
