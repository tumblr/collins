#!/bin/tcsh

set lead='10.60.29.155'

echo "starting leader ${lead}"
echo 'mkdir /home/petar/var ; mkdir /home/petar/var/doozer-bench' | ssh ${lead} 'tcsh'
echo "/home/petar/doozer-bench.devbox/doozerd -l ${lead}:8040 -w :8080 >& /home/petar/var/doozer-bench/doozerd &" | ssh ${lead} 'tcsh'
echo "done leader ${lead}"

foreach box (10.60.25.184 10.60.24.250 10.60.24.66)
	echo "starting ${box} doozerd"
	echo 'mkdir /home/petar/var ; mkdir /home/petar/var/doozer-bench' | ssh ${box} 'tcsh'
	echo "/home/petar/doozer-bench.devbox/doozerd -l ${box}:8040 -w :8080 -a ${lead}:8040 >& /home/petar/var/doozer-bench/doozerd &" | ssh ${box} 'tcsh'
	echo "done ${box} doozerd"
end
