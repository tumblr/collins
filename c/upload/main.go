package main

import (
	"flag"
	"tumblr/c"
)

var (
	flagSourceFile *string = flag.String("sfile", "", "Source file to upload")
	flagText       *string = flag.String("stext", "", "Source text to upload")
	flagHost       *string = flag.String("host", "", "Remote host")
	flagRemoteFile *string = flag.String("rfile", "", "Remote file")
)

func main() {
	flag.Parse()
	if *flagText == "" {
		c.UploadFile(*flagHost, *flagSourceFile, *flagRemoteFile)
	} else {
		c.UploadString(*flagHost, *flagText, *flagRemoteFile)
	}
}
