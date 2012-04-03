#!/bin/tcsh

set lead='10.60.29.155'

set box='10.60.24.250'
echo "/home/petar/doozer-bench.devbox/p_doozer_bench -doozerd ${lead}:8040 -k 50 -n 100 -id b3 -twin b4  >& /home/petar/var/doozer-bench/p_doozer_bench &" | ssh ${box} 'tcsh'

set box='10.60.24.66'
echo "/home/petar/doozer-bench.devbox/p_doozer_bench -doozerd ${lead}:8040 -k 50 -n 100 -id b4 -twin b3  >& /home/petar/var/doozer-bench/p_doozer_bench &" | ssh ${box}  'tcsh'
