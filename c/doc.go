package c

/*
	Executing daemons via scripts
	—————————————————————————————

	When launching daemons via scripts executed inside a shell, one often says

		some-daemon &
	
	Note however that even though the daemon's process leaves the shell, if you don't
	redirect its stdout and stderr, the shell will not return until the daemon dies 
	as the shell's stdin and stderr are now dups of the daemon's. So more often than not,
	you want to use

		some-daemon >& log &

*/
