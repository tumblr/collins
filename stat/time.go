package stat

import (
	"time"
)

type TimeSampler struct {
	m  Moment
	t0 *time.Time
}

func (x *TimeSampler) Init() {
	x.m.Init()
	x.t0 = nil
}

func (x *TimeSampler) Start() {
	if x.t0 != nil {
		panic("previous sample not completed")
	}
	t0 := time.Now()
	x.t0 = &t0
}

func (x *TimeSampler) Stop() {
	t1 := time.Now()
	diff := t1.Sub(*x.t0)
	x.t0 = nil
	x.m.Add(float64(diff))
}

func (x *TimeSampler) Moment() *Moment {
	return &x.m
}

func (x *TimeSampler) Average() float64 {
	return x.m.Average()
}

func (x *TimeSampler) StdDev() float64 {
	return x.m.StdDev()
}
