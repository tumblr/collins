package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"tumblr/script"
	"tumblr/shell"
)

// XXX: Can this framework be implemented entirely as a Go source file instead of a template?

// XXX: Add Run variant that prints out the program's combined output as it goes (std in grey, err in red, or prefixed)
func MustRun(prog, stdin string, args ...string) {
	combined, err := shell.RunCombined(prog, "", stdin, args...)
	if err != nil {
		panic(fmt.Sprintf("Running '%s %v': (%s)\nStdin:\n%s\nCombined:\n%s\n", prog, args, err, stdin, combined))
	}
}

// Upload recursively copies sourceDir/* to host:remoteDir/, creating remoteDir if necessary
func Upload(sourceDir string, hosts []string, remoteDir string) {
	for _, host := range hosts {
		fmt.Printf("Making directory(ies) '%s:%s'\n", host, remoteDir)
		MustRun("ssh", "mkdir -p " + remoteDir, host, "bash")
		fmt.Printf("rsync-ing local '%s' to remote '%s:%s'\n", sourceDir, host, remoteDir)
		MustRun("rsync", "", "-acrv", "--rsh=ssh", sourceDir + "/", host + ":" + remoteDir + "/")
	}
}

func MustParseJSON(source string, data interface{}) {
	err := json.Unmarshal([]byte(source), data)
	if err != nil {
		panic(fmt.Sprintf("exec spec parse (%s)", err))
	}
}

func Exec(cmd string) {
	fmt.Printf("Preparing command '%s'", cmd)
	execSource := script.MustExecute(cmd, nil)
	var exec []map[string]interface{}
	MustParseJSON(execSource, &exec)
	for _, job := range exec {
		fmt.Printf("  Executing '%s' on '%s'\n", job["script"], job["host"])
		remoteScript := script.MustExecute(job["script"].(string), job["args"])
		MustRun("ssh", remoteScript, job["host"].(string), "bash")
	}
}

var (
	flagSourceDir *string = flag.String("srcdir", "", "Local image directory")
	flagRemoteDir *string = flag.String("rmtdir", "", "Remote image directory")
	flagScript    *string = flag.String("script", "", "The scripts template file")
	flagCommand   *string = flag.String("cmd", "", "Command to execute (upload or user-defined)")
)

func usage(msg string) {
	fmt.Fprintf(os.Stderr, "%s\n", msg)
	flag.PrintDefaults()
	os.Exit(1)
}

func main() {
	flag.Parse()

	// Parse script file that describes entire experiment and participating machines
	if *flagScript == "" {
		usage("no script template given")
	}
	script.MustParse(*flagScript)

	// Read global parameters
	global := MustParseGlobal()

	// Switch on operation
	switch *flagCommand {
	case "upload":
		if len(global.Hosts) < 1 {
			usage("no participating hosts given")
		}
		Upload(*flagSourceDir, global.Hosts, *flagRemoteDir)
	default:
		Exec(*flagCommand)
	}
}

type Global struct {
	Hosts []string `json:"hosts"`
}

// Extract hosts from template
func MustParseGlobal() *Global {
	var global Global
	globalSource := script.MustExecute("global", nil)
	MustParseJSON(globalSource, &global)
	return &global
}
