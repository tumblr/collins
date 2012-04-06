package main

import (
	"flag"
	"os"
	"strings"
	"tumblr/shell"
)

func Upload(sourceDir string, hosts []string, remoteDir string) {
	?
	for _, host := range hosts {
		stdout, stderr, err := shell.Run("rsync", "", "", "-acrv", "--rsh=ssh", srcdir, host + ":" + remoteDir)
		if err != nil {
			?
		}
	}
}

var (
	flagOp        *string = flag.String("op", "", "One of: upload, start, stop, exec") 
	flagHosts     *string = flag.String("hosts", "", "Comma-separated list of deploy hosts")
	flagSourceDir *string = flag.String("srcdir", "", "Local image directory")
	flagRemoteDir *string = flag.String("rmtdir", "", "Remote image directory")
)

func usage(msg string) {
	fmt.Fprintf(os.Stderr, "%s\n", msg)
	flag.PrintDefaults()
	os.Exit(1)
}

func main() {
	flag.Parse()

	// Parse hosts
	hosts := strings.Split(*flagHosts, ",")
	if len(hosts) < 1 {
		usage("no deploy hosts given")
	}

	// Switch on operation
	switch *flagOp {
	case "upload":
		Upload(, hosts,)
	}
}
