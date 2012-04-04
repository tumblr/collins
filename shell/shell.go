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

func IsExitError(err error) bool {
	_, ok := err.(*exec.ExitError)
	return ok
}

// Run runs program prog in directory dir with arguments argv and returns its stdout and stderr
func Run(prog, dir string, stdin string, argv ...string) (stdout, stderr string, err error) {
	cmd := exec.Command(prog, argv...)
	cmd.Dir = dir

	stdinIO, err := cmd.StdinPipe()
	if err != nil {
		return "", "", err
	}
	stdoutIO, err := cmd.StdoutPipe()
	if err != nil {
		return "", "", err
	}
	stderrIO, err := cmd.StderrPipe()
	if err != nil {
		return "", "", err
	}
	if err := cmd.Start(); err != nil {
		return "", "", err
	}
	// Since Run is meant for non-interactive execution, we pump all the stdin first,
	// then we (sequentially) read all of stdout and then all of stderr.
	// XXX: Is it possible to block if the program's stderr buffer fills while we are
	// consuming the stdout?
	_, err = stdinIO.Write([]byte(stdin))
	if err != nil {
		return "", "", err
	}
	err = stdinIO.Close()
	if err != nil {
		return "", "", err
	}
	stdoutBuf, _ := ioutil.ReadAll(stdoutIO)
	stderrBuf, _ := ioutil.ReadAll(stderrIO)

	return string(stdoutBuf), string(stderrBuf), cmd.Wait()
}
