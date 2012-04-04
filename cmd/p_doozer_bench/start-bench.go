package main

import (
	"fmt"
	"log"
	"tumblr/shell"
	"tumblr/script"
)

var (
	Benches = []struct{
		Box     string
		Doozerd string
		Id      string
		Twin    string
	}{
		{
			Box:     "10.60.24.250",
			Doozerd: "10.60.29.155",
			Id:      "a",
			Twin:    "b",
		},
		{
			Box:     "10.60.24.66",
			Doozerd: "10.60.29.155", //"10.60.25.184",
			Id:      "b",
			Twin:    "a",
		},
	}
)

func main() {
	err := script.Parse("doozer-bench.t")
	if err != nil {
		log.Fatalf("cannot read template (%s)", err)
	}

	for _, b := range Benches {
		fmt.Printf("Scripting on %s\n", b.Box)
		remoteScript, err := script.Execute("start-bench", &b)
		if err != nil {
			log.Fatalf("executing bench %s template (%s)", b.Box, err)
		}
		stdout, stderr, err := shell.Run("ssh", "", remoteScript, b.Box, "tcsh")
		if err != nil {
			fmt.Printf("————————— stdout —————————\n%s\n", stdout)
			fmt.Printf("————————— stderr —————————\n%s\n", stderr)
			fmt.Printf("——————————————————————————\n")
			if !shell.IsExitError(err) {
				log.Fatalf("remote %s execution (%s)", b.Box, err)
			}
		}
	}
}
