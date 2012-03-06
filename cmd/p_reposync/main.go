package main

import (
	"flag"
	"io/ioutil"
	"log"
	"os"
	"os/exec"
	"path"
	"strings"
)

var (
	flagRsync       *string = flag.String("rsync", envOrDefault("rsync", "/use/bin/rsync"), "path to rsync executable")
	flagRemoteHost  *string = flag.String("host", "tumblr", "hostname of remote")
	flagRemoteSuper *string = flag.String("super", "/var/www/apps", "parent of repo directory on remote")
)

func envOrDefault(cmd, dfl string) string {
	p, err := exec.LookPath(cmd)
	if err != nil {
		return dfl
	}
	return p
}

func IsGitRepo(name string) (isRepo bool, err error) {
	fi, err := os.Stat(path.Join(name, ".git"))
	if err != nil {
		return false, err
	}
	return fi.IsDir(), nil
}

func IsGitRepoNoErr(name string) bool {
	isRepo, err := IsGitRepo(name)
	if err != nil {
		return false
	}
	return isRepo
}

func main() {
	flag.Parse()
	if len(flag.Args()) != 1 {
		log.Fatal("Missing target argument")
	}
	a := flag.Args()[0]
	if !path.IsAbs(a) {
		log.Fatal("Target is not absolute")
	}
	local := path.Clean(a)

	if !path.IsAbs(*flagRemoteSuper) {
		log.Fatal("Remote repo super-directory is not absolute")
	}
	p_ := strings.Split(local, "/")
	for i := 0; i < len(p_); i++ {
		localRepo := "/" + path.Join(p_[:len(p_)-i]...)
		if IsGitRepoNoErr(localRepo) {
			subPath := path.Join(p_[len(p_)-i-1:]...)
			remoteSuper := path.Clean(*flagRemoteSuper) + "/"
			remote := path.Join(remoteSuper, subPath)
			// log.Printf("rsync -acrv --rsh=ssh %s %s:%s\n", local, *flagRemoteHost, remote)
			err, rsyncStdout, rsyncStderr := Run(
					*flagRsync, "-acr",
					"--rsh=ssh", local,
					*flagRemoteHost + ":" + remote,
				)
			if err != nil {
				log.Printf("=== out ===\n%s\n=== err ===\n%s\n========\n", rsyncStdout, rsyncStderr)
				log.Fatalf("Error rsync-ing (%s)", err)
			}
			os.Exit(0)
		}
	}
	log.Fatal("Target is not inside a repo")
}

// TODO: This should be moved to a pkg
func Run(prog, dir string, argv ...string) (allout, allerr []byte, err error) {
	cmd := exec.Command(prog, argv...)
	cmd.Dir = dir
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, nil, err
	}
	stderr, err := cmd.StderrPipe()
	if err != nil {
		return nil, nil, err
	}
	if err := cmd.Start(); err != nil {
		return nil, nil, err
	}
	allout, _ = ioutil.ReadAll(stdout)
	allerr, _ = ioutil.ReadAll(stderr)
	return allout, allerr, cmd.Wait()
}
