package c

import (
	"fmt"
)

func Upload(host, sourceDir, remoteDir string) {
	fmt.Printf("Making directory(ies) '%s:%s'\n", host, remoteDir)
	MustRunScriptRemotely(host, `mkdir -p ` + remoteDir)
	fmt.Printf("rsync-ing local '%s' to remote '%s:%s'\n", sourceDir, host, remoteDir)
	MustRun("rsync", "", "-acrv", "--rsh=ssh", sourceDir + "/", host + ":" + remoteDir + "/")
}

func MustRunScriptRemotely(host, remoteScript string) {
	fmt.Printf("Executing remotely on %s:\n%s\n", host, remoteScript)
	MustRun("ssh", remoteScript, host, "tcsh")
}
