package main

import (
	"fmt"
	"os"
)

func main() {
	var f float64
	fmt.Scanf("%g", &f)
	fmt.Fprintf(os.Stderr, "hello %g\n", f)
	fmt.Fprintf(os.Stdout, "world %g\n", f)
}
