package git

import (
	"path"
	"os"
)

// IsRepo returns true if the supplied path is the root directory of a GIT repository
func IsRepo(name string) (isRepo bool, err error) {
	fi, err := os.Stat(path.Join(name, ".git"))
	if err != nil {
		return false, err
	}
	return fi.IsDir(), nil
}

func IsRepoNoErr(name string) bool {
	isRepo, err := IsRepo(name)
	if err != nil {
		return false
	}
	return isRepo
}
