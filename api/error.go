package api

import "errors"

var (
	ErrClosed = errors.New("connection already closed")
	ErrType   = errors.New("unexpected type")
)
