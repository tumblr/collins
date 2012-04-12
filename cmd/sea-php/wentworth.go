package main

import (
	"fmt"
	"os"
	"tumblr/wentworth"
)

func exitIfErr(err error) {
	if err == nil {
		return
	}
	fmt.Fprintf(os.Stderr, "Problem (%s)\n", err)
	os.Exit(1)
}

func main() {
	ww, err := wentworth.NewConn("service-staircar-de3dd588.d2.tumblr.net:9386")
	exitIfErr(err)

	r, err := ww.Get(1, 5)
	fmt.Printf("1: %v %v\n", r, err)
	r, err = ww.Get(1, 10)
	fmt.Printf("2: %v %v\n", r, err)

	err = ww.Close()
	exitIfErr(err)
}
