#!/bin/tcsh

foreach box (10.60.24.250 10.60.24.66)
	echo "killing bench ${box}"
	echo 'killall p_doozer_bench' | ssh ${box} 'tcsh'
	echo "done ${box}"
end
