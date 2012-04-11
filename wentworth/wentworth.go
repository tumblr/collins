package wentworth

import (
	"fmt"
	"net"
	"tumblr/api"
	"tumblr/3rdparty/thrift"
	"tumblr/wentworth/thrift/wentworth"
)

type Conn struct {
	transport thrift.TTransport
	client    *wentworth.WentworthServiceClient
}

func NewConn(hostPort string) (*Conn, error) {
	// Resolve address
	addr, err := net.ResolveTCPAddr("tcp", hostPort)
	if err != nil {
		return nil, err
	}

	// Make transport
	transport, err = thrift.NewTNonblockingSocketAddr(addr)
	if err != nil {
		return nil, err
	}
	transport = thrift.NewTFramedTransport(transport)

	// Make protocol
	protocolFactory := thrift.NewTBinaryProtocolFactoryDefault()

	// Make client
	client = wentworth.NewWentworthServiceClientFactory(transport, protocolFactory) 

	return &Conn{
		transport: transport,
		client:    client,
	}
}

func (c *Conn) Get(tumbleLogID int64, count int32) ([]wentworth.WNotification, err error) {
	if c.client == nil {
		return nil, api.ErrClosed
	}
	result, werr, err := c.client.Get(tumbleLogID, count)
	if err != nil {
		return err
	}
	if werr != nil {
		return NewError(werr)
	}
	...
	return ?, nil
}

func (c *Conn) Close() error {
	if c.transport == nil {
		return api.ErrClosed
	}
	err := c.transport.Close()
	c.transport = nil
	c.client = nil
	return err
}
