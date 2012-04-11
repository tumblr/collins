package main

import (
	"fmt"
	"tumblr/thrift"
	"tumblr/wentworth"
)

func main() {
	thriftProtocolFactory := thrift.NewTBinaryProtocolFactoryDefault()
	thriftTransportFactory := thrift.NewTTransportFactory()
}

func MakeWentworthClient(transportFactory thrift.TTransportFactory, protocolFactory thrift.TProtocolFactory) error {
}
