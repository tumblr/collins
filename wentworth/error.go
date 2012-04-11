package wentworth

import (
	"tumblr/wentworth/thrift/wentworth"
)

// Error represents a Wentworth service exception
type Error struct {
	description string
}

func NewError(werr *wentworth.WentworthException) *Error {
	return &Error{ werr.Description }
}

func (e *Error) Error() string {
	return e.description
}

func (e *Error) Exception() *wentworth.WentworthException {
	return &wentworth.WentworthException{
		Description: e.description,
	}
}
