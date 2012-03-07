package main

import (
	"flag"
	"fmt"
	"io/ioutil"
	"log"
	"os"
	"os/exec"
	"path"
	"path/filepath"
	"strings"
)

// XXX: Check that the repository is tumblr and not something else

var (
	flagRsync       *string = flag.String("rsync", envOrDefault("rsync", "/use/bin/rsync"), "path to rsync executable")
	flagRemoteHost  *string = flag.String("host", "tumblr", "hostname of remote")
	flagRemoteSuper *string = flag.String("super", "/var/www/apps", "parent of repo directory on remote")
	flagRepos       *string = flag.String("repos", "tumblr", "'"+string(filepath.ListSeparator) + "'-separated list of repo names to sync")
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
	repos := filepath.SplitList(*flagRepos)
	if len(repos) == 0 {
		log.Fatal("List of repos empty")
	}

	if !path.IsAbs(*flagRemoteSuper) {
		log.Fatal("Remote repo super-directory is not absolute")
	}
	p_ := strings.Split(local, "/")
	for i := 1; i < len(p_); i++ {
		repo := p_[len(p_)-i-1]
		localRepo := "/" + path.Join(p_[:len(p_)-i]...)
		if InStringSlice(repo, repos) && IsGitRepoNoErr(localRepo) {
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
				log.Fatalf("rsync error (%s)", err)
			}
			fmt.Printf("rsync OK: %s ——> %s:%s\n", local, *flagRemoteHost, remote)
			os.Exit(0)
		}
	}
	// If target is not inside a repo, don't complain
	os.Exit(0)
}

func InStringSlice(s string, list []string) bool {
	for _, x := range list {
		if s == x {
			return true
		}
	}
	return false
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
