package main

import (
	"time"
	"tumblr/zookeeper"
)

func main() {
	zk, session, err := zookeeper.Dial("localhost:2181", 5*time.Second)
	if err != nil {
		println("Couldn't connect: ", err)
		return
	}

	defer zk.Close()

	// Wait for connection.
	println("waiting for connection ...")
	event := <-session
	println("session event arrived")
	if event.State != zookeeper.STATE_CONNECTED {
		println("Couldn't connect")
		return
	}
	println("connected successfully")

	_, err = zk.Create("/counter", "0", 0, zookeeper.WorldACL(zookeeper.PERM_ALL))
	if err != nil {
		println("problem creating: ", err)
	} else {
		println("created successfully")
	}
}
