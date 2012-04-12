package wentworth

import (
	"net"
	"tumblr/api"
	"tumblr/3rdparty/thrift"
	"tumblr/wentworth/thrift/wentworth"
)

type Listener struct {
}

// HALT midway
func Listen(hostPort string) (*Listener, error) {
	// Resolve address
	addr, err := net.ResolveTCPAddr("tcp", hostPort)
	if err != nil {
		return nil, err
	}

	serverTransport, err := thrift.NewTServerSocketAddr(addr)
	if err != nil {
		return nil, err
	}

	protocolFactory := thrift.NewTBinaryProtocolFactoryDefault()
	transportFactory := thrift.NewTFramedTransportFactory(thrift.NewTTransportFactory())

	// TODO: This line creates a single-threaded server. Probably suffices. What to do for multi-threaded?
	server := thrift.NewTSimpleServer4(processor, serverTransport, transportFactory, protocolFactory)

	x := &Listener{
	}

	return x, nil
}

func (x *Listener) Accept() (*Conn, error) {
}


func (x *Listener) Close() error {
}
