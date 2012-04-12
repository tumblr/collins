package wentworth

import (
	"net"
	"tumblr/api"
	"tumblr/3rdparty/thrift"
	"tumblr/wentworth/thrift/wentworth"
)

type Conn struct {
	transport thrift.TTransport
	client    *wentworth.WentworthServiceClient
}

func Dial(hostPort string) (*Conn, error) {
	// Resolve address
	addr, err := net.ResolveTCPAddr("tcp", hostPort)
	if err != nil {
		return nil, err
	}

	// Make transport
	var transport thrift.TTransport
	transport, err = thrift.NewTNonblockingSocketAddr(addr)
	if err != nil {
		return nil, err
	}
	transport = thrift.NewTFramedTransport(transport)

	err = transport.Open()
	if err != nil {
		return nil, err
	}

	// Make protocol
	protocolFactory := thrift.NewTBinaryProtocolFactoryDefault()

	// Make client
	client := wentworth.NewWentworthServiceClientFactory(transport, protocolFactory) 

	return &Conn{
		transport: transport,
		client:    client,
	}, nil
}

func (c *Conn) Get(tumbleLogID int64, count int32) (slice []*wentworth.WNotification, err error) {
	if c.client == nil {
		return nil, api.ErrClosed
	}
	result, werr, err := c.client.Get(tumbleLogID, count)
	if err != nil {
		return nil, err
	}
	if werr != nil {
		return nil, NewError(werr)
	}
	return TListToSliceWNotification(result)
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

func TListToSliceWNotification(tlist thrift.TList) (slice []*wentworth.WNotification, err error) {
	slice = make([]*wentworth.WNotification, tlist.Len())
	for i := 0; i < len(slice); i++ {
		e, ok := tlist.At(i).(*wentworth.WNotification)
		if !ok {
			return nil, api.ErrType
		}
		slice[i] = e
	}
	return slice, nil
}
