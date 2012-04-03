#!/bin/tcsh

foreach box (10.60.29.155 10.60.25.184 10.60.24.250 10.60.24.66)
	echo 'killall doozerd' | ssh ${box} 'tcsh'
end
