package main

import "sync"

type Error struct {
	errorTxt string
}

func (e Error) Error() string {
	return e.errorTxt
}

type TimestampGen struct {
	nodeSaltWidth  uint8 // bits of salt
	indexWidth     uint8 // bits of index
	timestampDelta int64 // adjust the epoch

	maxIndex uint64 // the largest index which will fit in indexWidth
	maxSalt  uint64 // the largest salt which will fit in nodeSaltWidth

	nodeId    uint64 // the unique id for this node
	startId   uint64 // first generated id
	lastId    uint64 // last generated id
	lastTime  int64  // the last used time
	lastIndex uint64 // the index of the last id

	mu sync.RWMutex
}

func NewTimestampGen(nodeId uint64, saltWidth uint8, indexWidth uint8) *TimestampGen {
	gen := &TimestampGen{nodeId: nodeId, nodeSaltWidth: saltWidth, indexWidth: indexWidth}

	gen.maxSalt = 1 << gen.nodeSaltWidth
	gen.maxIndex = 1 << gen.indexWidth

	return gen
}

// use the current time to generate the next id
func (gen *TimestampGen) NextId(time int64) (uint64, error) {
	if gen.lastTime > time {
		return 0, Error{"time reversal"}
	}

	gen.mu.Lock()
	defer gen.mu.Unlock()

	if gen.lastTime != time {
		gen.lastIndex = 0
	}

	gen.lastIndex++
	if gen.lastIndex > gen.maxIndex {
		return 0, Error{"index overflow"}
	}

	gen.lastTime = time

	adjustedTime := uint64(time - gen.timestampDelta)

	id := adjustedTime << (gen.nodeSaltWidth + gen.indexWidth)
	id |= gen.lastIndex << gen.nodeSaltWidth
	id |= gen.nodeId

	if gen.startId == 0 {
		gen.startId = id
	}

	return id, nil
}

// gives the last generated id or zero if no id has been generated
func (gen *TimestampGen) GetId() uint64 {
	return gen.lastId
}

// gives the first generated id or zero if no id has been generated
func (gen *TimestampGen) StartId() uint64 {
	return gen.startId
}
