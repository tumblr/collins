package main

import (
	"log"
	"tumblr/shell"
	"tumblr/script"
)

var (
	Box = []string{ "10.60.29.155", "10.60.25.184", "10.60.24.250", "10.60.24.66" }
)

// TODO: 
//   Add template file name in flag
//   Wrap reading of flags and parsing of templates into one call on startup
//   Hide the ScriptTemplate and just use global-level routines
//   Combine all scripts in a single exe
func main() {
	err := script.Parse("doozer-bench.t")
	if err != nil {
		log.Fatalf("cannot read template (%s)", err)
	}

	// Start leader
	fmt.Printf("Scripting on leader %s\n", Box[0])
	remoteLeaderScript, err := script.Execute("start-doozerd-leader", script.M{ "Bind": Box[0] })
	if err != nil {
		log.Fatalf("executing leader template (%s)", err)
	}
	_, _, err = shell.Run("ssh", "", remoteLeaderScript, Box[0], "tcsh")
	if err != nil {
		log.Fatalf("remote leader execution (%s)", err)
	}

	// Start slaves
	for i := 1; i < len(Box); i++ {
		fmt.Printf("Scripting on %s\n", Box[i])
		remoteSlaveScript, err := script.Execute("start-doozerd-slave", script.M{ "Bind": Box[i], "Leader": Box[0] })
		if err != nil {
			log.Fatalf("executing slave %s template (%s)", Box[i], err)
		}
		_, _, err = shell.Run("ssh", "", remoteSlaveScript, Box[i], "tcsh")
		if err != nil {
			log.Fatalf("remote slave %s execution (%s)", Box[i], err)
		}
	}
}
