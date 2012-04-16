package main

import (
	"flag"
	"fmt"
	"os"
	"path"
	. "tumblr/c"
)

/* Zookeeper-specific */

var (
	Hosts = []string{ 
		"service-sea-f47ef374.d2.tumblr.net",
		"service-sea-c80b99b0.d2.tumblr.net",
		"service-sea-a63e1541.d2.tumblr.net",
		"service-sea-94d5250f.d2.tumblr.net",
		"service-sea-4eda8aff.d2.tumblr.net",
		"service-sea-0dfc3285.d2.tumblr.net",
	}
	SourceDir = ""
	RemoteDir = "sea-zookeeper.jail"
)

// Upload SEA Zookeeper bundle
func cmdUpload() {
	for _, host := range Hosts {
		fmt.Printf("Uploading to %s\n", host)
		UploadDir(host, SourceDir, RemoteDir)
	}
}

// Start Zookeeper
const startZookeeperScript =
`
cd {{.Dir}}
mkdir -p var/zookeeper
mkdir -p var/commander
echo {{.ServerID}} > var/zookeeper/myid
./start-multiple.sh >& var/commander/zookeeper.log &
`

const zookeeperConfig =
`
tickTime=2000
initLimit=5
syncLimit=2
dataDir=./var/zookeeper
clientPort=2181
{{range .Workers}}
	server.{{.No}}={{.Host}}:2888:3888
{{end}}
`

type worker struct {
	No   int
	Host string
}

func cmdStartZookeeper() {
	workers := make([]worker, len(Hosts))
	for i, host := range Hosts {
		workers[i].No = i
		workers[i].Host = host
	}
	for i := 0; i < len(Hosts); i++ {
		fmt.Printf("Starting %s\n", Hosts[i])

		// Prepare and place host-specific Zookeeper config file
		zkcfg := MustParseAndExecute(zookeeperConfig, M{ "Workers": workers })
		UploadString(Hosts[i], zkcfg, path.Join(RemoteDir, "zoo-multiple.cfg"))

		// Launch Zookeeper
		MustRunScriptRemotely(Hosts[i], 
			MustParseAndExecute(startZookeeperScript, M{ "Dir": RemoteDir, "ServerID": i+1 }),
		)
	}
}

// Wipe Zookeeper file database
const wipeZookeeperScript =
`
cd {{.Dir}}
rm -rf var/zookeeper/* || /bin/true
`

func cmdWipeZookeeper() {
	for i := 0; i < len(Hosts); i++ {
		fmt.Printf("Wiping %s\n", Hosts[i])
		MustRunScriptRemotely(Hosts[i], wipeZookeeperScript) 
	}
}

// Stop Zookeeper
const stopZookeeperScript = "kill `ps ax | grep zookeeper | grep java | awk '{ print $1 }'`"

func cmdStopZookeeper() {
	for i := 0; i < len(Hosts); i++ {
		fmt.Printf("Stoping %s\n", Hosts[i])
		MustRunScriptRemotely(Hosts[i], stopZookeeperScript) 
	}
}

// Command-line
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
	case "WipeZookeeper":
		cmdWipeZookeeper()
	default:
		usage("unrecognized command")
	}
}
