package main

import (
	"testing"
	"time"
)

func TestStartId(t *testing.T) {
	gen := NewTimestampGen(8, 5, 10)
	time := int64(66666)

	if gen.startId != 0 {
		t.Errorf("startId isn't correct: %d", gen.startId)
	}

	nextId, err := gen.NextId(time)

	if err != nil {
		t.Error("could not generated id: %s", err.Error())
	}

	if nextId != gen.startId {
		t.Errorf("startId (%d) doesn't match first generated id (%d)", gen.startId, nextId)
	}

	nextId, err = gen.NextId(time)
	if err != nil {
		t.Error("could not generated id: %s", err.Error())
	}
	if gen.startId == nextId {
		t.Errorf("startId (%d) should not match nextIdb", gen.startId)
	}
}

func BenchmarkTimestampGen(b *testing.B) {
	gen := NewTimestampGen(3, 5, 10)
	for i := 0; i < b.N; i++ {
		gen.NextId(time.Now().UnixNano())
	}
}
