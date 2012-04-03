#!/bin/tcsh

set lead='10.60.29.155'

echo 'mkdir /home/petar/var ; mkdir /home/petar/var/doozer-bench' | ssh ${lead} 'tcsh'
echo '/home/petar/doozer-bench.devbox/doozerd -l 127.0.0.1:8040 -w 127.0.0.1:8080 >& /home/petar/var/doozer-bench/doozerd &' | ssh ${lead} 'tcsh'

foreach box (10.60.25.184 10.60.24.250 10.60.24.66)
	echo 'mkdir /home/petar/var ; mkdir /home/petar/var/doozer-bench' | ssh ${box} 'tcsh'
	echo "/home/petar/doozer-bench.devbox/doozerd -l 127.0.0.1:8040 -w 127.0.0.1:8080 -a ${lead}:8040 >& /home/petar/var/doozer-bench/doozerd &" | ssh ${box} 'tcsh'
end
