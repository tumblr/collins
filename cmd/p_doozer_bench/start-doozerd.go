package main

import (
	"fmt"
	"log"
	//"time"
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
//   Run command with mixed output
func main() {
	err := script.Parse("doozer-bench.t")
	if err != nil {
		log.Fatalf("cannot read template (%s)", err)
	}

	startLeader(Box[0])
	startSlave(Box[1], Box[0])
	//fmt.Printf("sleep for a bit\n")
	//time.Sleep(time.Second*5)
	//inviteMember(Box[0], Box[0])
	startSlave(Box[2], Box[0])
	startSlave(Box[3], Box[0])
}

func inviteMember(box string, leader string) {
	fmt.Printf("inviteMember %s\n", leader)
	remoteScript, err := script.Execute("invite-doozerd-member", script.M{ "Leader": leader })
	if err != nil {
		log.Fatalf("executing invite member template (%s)", err)
	}
	stdout, stderr, err := shell.Run("ssh", "", remoteScript, box, "tcsh")
	if err != nil {
		fmt.Printf("————————— stdout —————————\n%s\n", stdout)
		fmt.Printf("————————— stderr —————————\n%s\n", stderr)
		fmt.Printf("——————————————————————————\n")
		if !shell.IsExitError(err) {
			log.Fatalf("remote leader execution (%s)", err)
		}
	}
}

func startLeader(leader string) {
	fmt.Printf("startLeader %s\n", leader)
	remoteScript, err := script.Execute("start-doozerd-leader", script.M{ "Bind": leader })
	if err != nil {
		log.Fatalf("executing leader template (%s)", err)
	}
	stdout, stderr, err := shell.Run("ssh", "", remoteScript, leader, "tcsh")
	if err != nil {
		fmt.Printf("————————— stdout —————————\n%s\n", stdout)
		fmt.Printf("————————— stderr —————————\n%s\n", stderr)
		fmt.Printf("——————————————————————————\n")
		if !shell.IsExitError(err) {
			log.Fatalf("remote leader execution (%s)", err)
		}
	}
}

func startSlave(box string, leader string) {
	fmt.Printf("startSlave %s -> %s\n", box, leader)
	remoteScript, err := script.Execute("start-doozerd-slave", script.M{ "Bind": box, "Leader": leader })
	if err != nil {
		log.Fatalf("executing slave %s template (%s)", box, err)
	}
	stdout, stderr, err := shell.Run("ssh", "", remoteScript, box, "tcsh")
	if err != nil {
		fmt.Printf("————————— stdout —————————\n%s\n", stdout)
		fmt.Printf("————————— stderr —————————\n%s\n", stderr)
		fmt.Printf("——————————————————————————\n")
		if !shell.IsExitError(err) {
			log.Fatalf("remote slave %s execution (%s)", box, err)
		}
	}
}
