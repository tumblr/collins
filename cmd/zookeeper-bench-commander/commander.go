package main

import (
	"bytes"
	"flag"
	"fmt"
	"os"
	"text/template"
	"tumblr/shell"
)

// XXX: Add Run variant that prints out the program's combined output as it goes (std in grey, err in red, or prefixed)
func MustRun(prog, stdin string, args ...string) {
	combined, err := shell.RunCombined(prog, "", stdin, args...)
	if err != nil {
		panic(fmt.Sprintf("Running '%s %v': (%s)\nStdin:\n%s\nCombined:\n%s\n", prog, args, err, stdin, combined))
	}
}

func MustParse(source string) *template.Template {
	t := template.New("noname")
	return template.Must(t.Parse(source))
}

type M map[string]interface{}

func MustParseAndExecute(source string, data interface{}) string {
	t := MustParse(source)
	var w bytes.Buffer
	err := t.Execute(&w, data)
	if err != nil {
		panic(fmt.Sprintf("template execute (%s)", err))
	}
	return string(w.Bytes())
}

func MustRunScriptRemotely(host, remoteScript string) {
	MustRun("ssh", remoteScript, host, "tcsh")
}

func Upload(host, sourceDir, remoteDir string) {
	fmt.Printf("Making directory(ies) '%s:%s'\n", host, remoteDir)
	MustRunScriptRemotely(host, `mkdir -p ` + remoteDir)
	fmt.Printf("rsync-ing local '%s' to remote '%s:%s'\n", sourceDir, host, remoteDir)
	MustRun("rsync", "", "-acrv", "--rsh=ssh", sourceDir + "/", host + ":" + remoteDir + "/")
}

/* Zookeeper-specific */

var (
	Hosts = []string{ "10.60.29.155", "10.60.25.184", "10.60.24.250", "10.60.24.66" }
	SourceDir = "zookeeper-bench.bundle"
	RemoteDir = "zookeeper-bench.jail"
)

func cmdUpload() {
	for _, host := range Hosts {
		Upload(host, SourceDir, RemoteDir)
	}
}

const startZookeeperScript =
	`
	cd + {{.Dir}}
	mkdir -p var/zookeeper
	rm -rf var/zookeeper/*
	echo {{.ServerID}} > var/zookeeper/myid
	./start-multiple.sh &
	`

func cmdStartZookeeper() {
	for i := 0; i < len(Hosts); i++ {
		MustRunScriptRemotely(Hosts[i], 
			MustParseAndExecute(startZookeeperScript, M{ "Dir": RemoteDir, "ServerId": i+1 }),
		)
	}
}

const stopZookeeperScript = "kill `ps ax | grep zookeeper | grep java | awk '{ print $1 }'`"

func cmdStopZookeeper() {
	for i := 0; i < len(Hosts); i++ {
		MustRunScriptRemotely(Hosts[i], stopZookeeperScript) 
	}
}

const startBenchmarkScript =
	`
	cd {{.Dir}}
	./zookeeper-bench -id {{.Id}} -twin {{.Twin}} -k 50 -n 100 -zk &
	`
func cmdStartBenchmark() {
	for i := 0; i < 2; i++ {
		MustRunScriptRemotely(Hosts[i+2], 
			MustParseAndExecute(startBenchmarkScript, M{ "Dir": RemoteDir, "Id": i, "Twin": 1-i }),
		)
	}
}

func usage(msg string) {
	fmt.Fprintf(os.Stderr, "%s\n", msg)
	flag.PrintDefaults()
	os.Exit(1)
}

var (
	flagCmd *string = flag.String("cmd", "", "Command: Upload, StartZookeeper, StopZookeeper, StartBenchmark")
)

// XXX: Add general boilerplate to run the available commands
func main() {
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
	default:
		usage("unrecognized command")
	}
}
