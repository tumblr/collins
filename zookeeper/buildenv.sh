#!/bin/sh
setenv CGO_CFLAGS '-I/Users/petar/local/include/c-client-src'
setenv CGO_LDFLAGS '-L/Users/petar/local/lib -lm -lpthread -lzookeeper_mt'
