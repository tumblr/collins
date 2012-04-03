#!/bin/tcsh

echo "/home/petar/doozer-bench.devbox/p_doozer_bench -doozerd 127.0.0.1:8040 -k 100 -n 10000 -id b3 -twin b4  >& /home/petar/var/doozer-bench/p_doozer_bench &" | ssh 10.60.24.250 'tcsh'
echo "/home/petar/doozer-bench.devbox/p_doozer_bench -doozerd 127.0.0.1:8040 -k 100 -n 10000 -id b4 -twin b3  >& /home/petar/var/doozer-bench/p_doozer_bench &" | ssh 10.60.24.66  'tcsh'
