ackage c

import (
	"fmt"
	"io/ioutil"
	"path"
	"os"
)

func UploadDir(host, sourceDir, remoteDir string) {
	fmt.Printf("Uploading directory(ies) '%s:%s'\n", host, remoteDir)
	MustRunScriptRemotely(host, `mkdir -p ` + remoteDir)
	fmt.Printf("  rsync local '%s' to remote '%s:%s'\n", sourceDir, host, remoteDir)
	MustRun("rsync", "", "-acrv", "--rsh=ssh", sourceDir + "/", host + ":" + remoteDir + "/")
}

func UploadFile(host, sourceFile, remoteFile string) {
	fmt.Printf("Uploading file '%s' to '%s:%s'\n", sourceFile, host, remoteFile)
	remoteDir, _ := path.Split(remoteFile)
	fmt.Printf("  mkdir '%s:%s'\n", host, remoteDir)
	MustRunScriptRemotely(host, `mkdir -p ` + remoteDir)
	fmt.Printf("  rsync '%s' to remote '%s:%s'\n", sourceFile, host, remoteFile)
	MustRun("rsync", "", "-acv", "--rsh=ssh", sourceFile, host + ":" + remoteFile)
}

// UploadString uploads the string s onto a file called remoteFile on host
func UploadString(host, s, remoteFile string) {
	// Prepare tmp file
	f, err := ioutil.TempFile("", "tumblr-upload-")
	if err != nil {
		panic("cannot create tmp file")
	}
	defer f.Close()
	tmpname := f.Name()
	defer os.Remove(tmpname)

	_, err = f.WriteString(s)
	if err != nil {
		panic("cannot write to tmp file")
	}
	UploadFile(host, tmpname, remoteFile)
}

func MustRunScriptRemotely(host, remoteScript string) {
	fmt.Printf("Executing remotely on %s:\n%s\n", host, remoteScript)
	MustRun("ssh", remoteScript + "\n", host, "tcsh")
}
