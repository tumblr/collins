package redis

import (
	"bufio"
	"bytes"
	"errors"
	"fmt"
	"net"
	"strconv"
	"time"
)

type Conn struct {
	conn net.Conn
	r    *bufio.Reader
}

const (
	CRLF        = "\r\n"	// Line terminator in Redis wire protocol
	MaxArgSize  = 64000	// Maximum acceptable size of a bulk response
	MaxArgCount = 64	// Maximum acceptable number of arguments in a multi-bulk response
)

var (
	ErrFormat = errors.New("format error")
	ErrSize   = errors.New("size out of bounds")
)

// The different kinds of Redis responses are captured in respective Go types
type Status    string
type Error     string
type Integer   int
type Bulk      string
type MultiBulk []Bulk

func Dial(addr string) (conn *Conn, err error) {
	c, err := net.DialTimeout("tcp", addr, time.Second*5)
	if err != nil {
		return nil, err
	}
	return &Conn{ conn:c, r:bufio.NewReader(c) }, nil
}

func (c *Conn) Close() error {
	return c.conn.Close()
}

func (c *Conn) WriteMultiBulk(args ...string) error {
	var w bytes.Buffer
	w.WriteString("*")
	w.WriteString(strconv.Itoa(len(args)))
	w.WriteString(CRLF)
	for _, a := range args {
		w.WriteString("$")
		w.WriteString(strconv.Itoa(len(a)))
		w.WriteString(CRLF)
		w.Write([]byte(a))
		w.WriteString(CRLF)
	}
	_, err := c.conn.Write(w.Bytes())
	return err
}

func (c *Conn) ReadResponse() (resp interface{}, err error) {
	ch, err := c.r.ReadByte()
	if err != nil {
		return nil, err
	}

	switch ch {
	case '+':
		line, isPrefix, err := c.r.ReadLine()
		if err != nil {
			return nil, err
		}
		if isPrefix {
			return nil, ErrSize
		}
		return Status(line), nil
	case '-':
		line, isPrefix, err := c.r.ReadLine()
		if err != nil {
			return nil, err
		}
		if isPrefix {
			return nil, ErrSize
		}
		return Error(line), nil
	case ':':
		line, isPrefix, err := c.r.ReadLine()
		if err != nil {
			return nil, err
		}
		if isPrefix {
			return nil, ErrSize
		}
		i, err := strconv.Atoi(string(line))
		if err != nil {
			return nil, ErrFormat
		}
		return Integer(i), nil
	case '$':
		c.r.UnreadByte()
		return c.ReadBulk()
	case '*':
		c.r.UnreadByte()
		return c.ReadMultiBulk()
	}
	return nil, ErrFormat
}

func (c *Conn) ReadMultiBulk() (multibulk MultiBulk, err error) {
	// Read first line
	line, isPrefix, err := c.r.ReadLine()
	if err != nil {
		return nil, err
	}
	if isPrefix {
		return nil, ErrSize
	}
	if len(line) == 0 || line[0] != '*' {
		return nil, ErrFormat
	}
	// Parse number of bulk arguments
	k, err := strconv.Atoi(string(line[1:]))
	if err != nil {
		return nil, ErrFormat
	}
	if k < 0 || k > MaxArgCount {
		return nil, ErrSize
	}
	multibulk = make(MultiBulk, k)
	for i := 0; i < k; i++ {
		bulk, err := c.ReadBulk()
		if err != nil {
			return nil, err
		}
		multibulk[i] = bulk
	}
	return multibulk, nil
}

func (c *Conn) ReadBulk() (bulk Bulk, err error) {
	// Read first line containing argument size
	line, isPrefix, err := c.r.ReadLine()
	if err != nil {
		return "", err
	}
	if isPrefix {
		return "", ErrSize
	}
	// Parse argument size and enforce safety bounds
	if len(line) == 0 || line[0] != '$' {
		return "", ErrFormat
	}
	arglen, err := strconv.Atoi(string(line[1:]))
	if err != nil {
		return "", ErrFormat
	}
	if arglen < 0 || arglen > MaxArgSize {
		return "", ErrSize
	}
	// Read argument data and verify terminating characters
	arg := make([]byte, arglen+2)
	n, err := c.r.Read(arg)
	if err != nil {
		return "", err
	}
	if n != arglen+2 || string(arg[len(arg)-2:]) != CRLF {
		return "", ErrFormat
	}
	return Bulk(arg[:arglen]), nil
}

func (c *Conn) ReadOK() error {
	resp, err := c.ReadResponse()
	if err != nil {
		return err
	}
	status, ok := resp.(Status)
	if !ok || status != "OK" {
		return errors.New(fmt.Sprintf("Non-OK response (%v)", resp))
	}
	return nil
}

func ResponseString(resp interface{}) string {
	if resp == nil {
		return "nil"
	}
	switch t := resp.(type) {
	case Bulk:
		return fmt.Sprintf("Bulk: %s", t)
	case MultiBulk:
		var w bytes.Buffer
		fmt.Fprintf(&w, "MultiBulk: ")
		for _, b := range t {
			fmt.Fprintf(&w, "%s ", b)
		}
		return string(w.Bytes())
	case Integer:
		return fmt.Sprintf("Integer=%d", t)
	case Error:
		return fmt.Sprintf("Error=%s", t)
	case Status:
		return fmt.Sprintf("Status=%s", t)
	}
	panic("unexpected response type")
}
