package shell

import (
	"os/exec"
	"io/ioutil"
)

// WhichOrDefault looks up the absolute path of cmd in the PATH environment variable and returns it
// if found. Otherwise, the default dflt is returned.
func WhichOrDefault(cmd, dflt string) string {
	p, err := exec.LookPath(cmd)
	if err != nil {
		return dflt
	}
	return p
}

// Run2 runs program prog in directory dir with arguments argv and returns its stdout and stderr
func Run2(prog, dir string, argv ...string) (allout, allerr []byte, err error) {
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
