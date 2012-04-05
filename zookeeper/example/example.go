package main

import (
	"tumblr/zookeeper"
)

func main() {
	zk, session, err := zookeeper.Init("localhost:2181", 5000)
	if err != nil {
		println("Couldn't connect: " + err.String())
		return
	}

	defer zk.Close()

	// Wait for connection.
	event := <-session
	if event.State != zookeeper.STATE_CONNECTED {
		println("Couldn't connect")
		return
	}

	_, err = zk.Create("/counter", "0", 0, zookeeper.WorldACL(zookeeper.PERM_ALL))
	if err != nil {
		println(err.String())
	} else {
		println("Created!")
	}
}
