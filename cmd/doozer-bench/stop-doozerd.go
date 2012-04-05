package main

import (
	"fmt"
	"log"
	"tumblr/shell"
	"tumblr/script"
)

var (
	Box = []string{ "10.60.29.155", "10.60.25.184", "10.60.24.250", "10.60.24.66" }
)

func main() {
	err := script.Parse("doozer-bench.t")
	if err != nil {
		log.Fatalf("cannot read template (%s)", err)
	}

	// Start slaves
	for i := 0; i < len(Box); i++ {
		fmt.Printf("Scripting on %s\n", Box[i])
		remoteStopScript, err := script.Execute("stop-doozerd", script.M{ "Bind": Box[i] })
		if err != nil {
			log.Fatalf("executing slave %s template (%s)", Box[i], err)
		}
		stdout, stderr, err := shell.Run("ssh", "", remoteStopScript, Box[i], "tcsh")
		if err != nil {
			fmt.Printf("————————— stdout —————————\n%s\n", stdout)
			fmt.Printf("————————— stderr —————————\n%s\n", stderr)
			fmt.Printf("——————————————————————————\n")
			if !shell.IsExitError(err) {
				log.Fatalf("remote slave %s execution (%s)", Box[i], err)
			}
		}
	}
}
