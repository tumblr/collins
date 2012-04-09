package c

import (
	"fmt"
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

	stdinWriter, err := cmd.StdinPipe()
	if err != nil {
		return "", "", err
	}
	stdoutReader, err := cmd.StdoutPipe()
	if err != nil {
		return "", "", err
	}
	stderrReader, err := cmd.StderrPipe()
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
	_, err = stdinWriter.Write([]byte(stdin))
	if err != nil {
		return "", "", err
	}
	err = stdinWriter.Close()
	if err != nil {
		return "", "", err
	}
	stdoutBuf, _ := ioutil.ReadAll(stdoutReader)
	stderrBuf, _ := ioutil.ReadAll(stderrReader)

	return string(stdoutBuf), string(stderrBuf), cmd.Wait()
}

// RunCombined runs program prog in directory dir with arguments argv and returns its combined stdout and stderr
func RunCombined(prog, dir string, stdin string, argv ...string) (combined string, err error) {
	cmd := exec.Command(prog, argv...)
	cmd.Dir = dir

	if len(stdin) > 0 {
		stdinWriter, err := cmd.StdinPipe()
		if err != nil {
			return "", err
		}
		_, err = stdinWriter.Write([]byte(stdin))
		if err != nil {
			return "", err
		}
		err = stdinWriter.Close()
		if err != nil {
			return "", err
		}
	}

	comb, err := cmd.CombinedOutput()
	return string(comb), err
}

// MustRun is identical to RunCombined except it panics if the execution fails in anyway
// TODO: Add Run variant that prints out the program's combined output as it goes (std in grey, err in red, or prefixed)
func MustRun(prog, stdin string, args ...string) {
	combined, err := RunCombined(prog, "", stdin, args...)
	if err != nil {
		panic(fmt.Sprintf("Running '%s %v', causes (%s)\nStdin:\n%s\nStdout+Stderr:\n%s\n", prog, args, err, stdin, combined))
	}
}

func MustRunCombined(prog, stdin string, args ...string) string {
	combined, err := RunCombined(prog, "", stdin, args...)
	if err != nil {
		panic(fmt.Sprintf("Running '%s %v', causes (%s)\nStdin:\n%s\nStdout+Stderr:\n%s\n", prog, args, err, stdin, combined))
	}
	return combined
}

