package main

import (
	"flag"
	"fmt"
	"os"
	. "tumblr/c"
)

/* Zookeeper-specific */

var (
	Hosts = []string{ "10.60.29.155", "10.60.25.184", "10.60.24.250", "10.60.24.66" }
	SourceDir = ""
	RemoteDir = "zookeeper-bench.jail"
)

func cmdUpload() {
	for _, host := range Hosts {
		Upload(host, SourceDir, RemoteDir)
	}
}

const startZookeeperScript =
	`
	cd {{.Dir}}
	mkdir -p var/zookeeper
	mkdir -p var/commander
	rm -rf var/zookeeper/* || /bin/true
	echo {{.ServerID}} > var/zookeeper/myid
	./start-multiple.sh >& var/commander/zookeeper-multiple.log &
	`

func cmdStartZookeeper() {
	for i := 0; i < len(Hosts); i++ {
		fmt.Printf("Starting %s\n", Hosts[i])
		MustRunScriptRemotely(Hosts[i], 
			MustParseAndExecute(startZookeeperScript, M{ "Dir": RemoteDir, "ServerID": i+1 }),
		)
	}
}

const stopZookeeperScript = "kill `ps ax | grep zookeeper | grep java | awk '{ print $1 }'`"

func cmdStopZookeeper() {
	for i := 0; i < len(Hosts); i++ {
		fmt.Printf("Stoping %s\n", Hosts[i])
		MustRunScriptRemotely(Hosts[i], stopZookeeperScript) 
	}
}

const startBenchmarkScript =
	`cd {{.Dir}} 
	setenv LD_LIBRARY_PATH .
	./zookeeper-bench -id {{.ID}} -twin {{.TwinID}} -k 50 -n 100 -zk ` + 
		`'{{ printCommaSeparated .HostPorts }}'` + 
		` >& var/commander/zookeeper-bench &
	`

func cmdStartBenchmark() {
	hostPorts := make([]string, len(Hosts))
	for i, _ := range Hosts {
		hostPorts[i] = Hosts[i] + ":2181"
	}
	for i := 0; i < 2; i++ {
		fmt.Printf("Starting benchmark on %s\n", Hosts[i+2])
		MustRunScriptRemotely(Hosts[i+2], 
			MustParseAndExecute(startBenchmarkScript, M{ "Dir": RemoteDir, "ID": i, "TwinID": 1-i, "HostPorts": hostPorts }),
		)
	}
}

const stopBenchmarkScript = "killall zookeeper-bench"

func cmdStopBenchmark() {
	for i := 0; i < 2; i++ {
		fmt.Printf("Stoping %s\n", Hosts[i+2])
		MustRunScriptRemotely(Hosts[i+2], stopBenchmarkScript) 
	}
}

func usage(msg string) {
	fmt.Fprintf(os.Stderr, "%s\n", msg)
	flag.PrintDefaults()
	os.Exit(1)
}

var (
	flagCmd    *string = flag.String("cmd", "", "Command: Upload, StartZookeeper, StopZookeeper, StartBenchmark")
	flagBundle *string = flag.String("bundle", "bundle", "Path to deploy bundle")
)

// TODO: Add general boilerplate to run the available commands
func main() {
	SourceDir = *flagBundle

	flag.Parse()
	switch *flagCmd {
	case "Upload":
		cmdUpload()
	case "StartZookeeper":
		cmdStartZookeeper()
	case "StopZookeeper":
		cmdStopZookeeper()
	case "StartBenchmark":
		cmdStartBenchmark()
	case "StopBenchmark":
		cmdStopBenchmark()
	default:
		usage("unrecognized command")
	}
}
