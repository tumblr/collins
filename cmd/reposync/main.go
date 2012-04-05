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
	"tumblr/git"
	"tumblr/shell"
)

var (
	flagRsync       *string = flag.String("rsync", shell.WhichOrDefault("rsync", "/use/bin/rsync"), "path to rsync executable")
	flagRemoteHost  *string = flag.String("host", "tumblr", "hostname of remote")
	flagRemoteSuper *string = flag.String("super", "/var/www/apps", "parent of repo directory on remote")
	flagRepos       *string = flag.String("repos", "tumblr", "'"+string(filepath.ListSeparator) + "'-separated list of repo names to sync")
	flagWhole       *bool   = flag.Bool("whole", true, "If set, the whole repository owning the target file will be synced")
)

func usage(msg string, err error) {
	if err != nil {
		fmt.Printf("%s (%s)\n", msg, err)
	} else if msg != "" {
		fmt.Printf("%s\n", msg)
	}
	fmt.Printf("%s absolute_file_to_sync\n", os.Args[0])
	flag.PrintDefaults()
	os.Exit(1)
}

func main() {
	flag.Parse()
	if len(flag.Args()) != 1 {
		usage("Missing target argument", nil)
	}
	a := flag.Args()[0]
	if !path.IsAbs(a) {
		usage("Target is not absolute", nil)
	}
	local := path.Clean(a)
	repos := filepath.SplitList(*flagRepos)
	if len(repos) == 0 {
		usage("List of repos empty", nil)
	}

	if !path.IsAbs(*flagRemoteSuper) {
		usage("Remote repo super-directory is not absolute", nil)
	}
	p_ := strings.Split(local, "/")
	for i := 1; i < len(p_); i++ {
		repo := p_[len(p_)-i-1]
		localRepo := "/" + path.Join(p_[:len(p_)-i]...)
		if InStringSlice(repo, repos) && git.IsRepoNoErr(localRepo) {
			subPath := path.Join(p_[len(p_)-i-1:]...)
			remoteSuper := path.Clean(*flagRemoteSuper) + "/"
			remote := path.Join(remoteSuper, subPath)
			rsyncStdout, rsyncStderr, err := shell.Run2(
					*flagRsync, "", "-acrv",
					"--rsh=ssh", local,
					*flagRemoteHost + ":" + remote,
				)
			if err != nil {
				log.Printf("=== out ===\n%s\n=== err ===\n%s\n========\n", string(rsyncStdout), string(rsyncStderr))
				usage("rsync error", err)
			}
			//fmt.Printf("rsync OK: %s ——> %s:%s\n", local, *flagRemoteHost, remote)
			//fmt.Printf("rsync -acrv --rsh=ssh %s %s:%s\n", local, *flagRemoteHost, remote)
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
