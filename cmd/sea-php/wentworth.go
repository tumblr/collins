package main

import (
	"fmt"
	"net"
	"os"
	"tumblr/thrift"
	"tumblr/sea/wentworth/thrift/wentworth"
)

func makeWentworthClient(hostPort string) (transport thrift.TTransport, client *wentworth.WentworthServiceClient, err error) {
	// Resolve address
	addr, err := net.ResolveTCPAddr("tcp", hostPort)
	if err != nil {
		return nil, nil, err
	}

	// Make transport
	transport, err = thrift.NewTNonblockingSocketAddr(addr)
	if err != nil {
		return nil, nil, err
	}
	transport = thrift.NewTFramedTransport(transport)

	// Make protocol
	protocolFactory := thrift.NewTBinaryProtocolFactoryDefault()

	// Make client
	client = wentworth.NewWentworthServiceClientFactory(transport, protocolFactory) 

	return transport, client, nil
}

func exitIfErr(err error) {
	if err == nil {
		return
	}
	fmt.Fprintf(os.Stderr, "Problem (%s)\n", err)
	os.Exit(1)
}

func main() {
	transport, client, err := makeWentworthClient("service-staircar-de3dd588.d2.tumblr.net:9386")
	exitIfErr(err)

	err = transport.Open()
	exitIfErr(err)

	r, werr, err := client.Get(1, 5)
	fmt.Printf("1: %v %v %v\n", r, werr, err)
	r, werr, err = client.Get(1, 10)
	fmt.Printf("2: %v %v %v\n", r, werr, err)

	err = transport.Close()
	exitIfErr(err)
}
