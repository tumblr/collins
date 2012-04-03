package stat

import (
	"math"
)

type Moment struct {
	Sum	float64
	SumSq	float64
	Weight  float64
}

func (x *Moment) Init() {
	x.Sum, x.SumSq, x.Weight = 0, 0, 0
}

func (x *Moment) Add(sample float64) {
	x.AddWeighted(sample, 1)
}

func (x *Moment) AddWeighted(sample float64, weight float64) {
	x.Sum += sample*weight
	x.SumSq += sample*sample*weight
	x.Weight += weight
}

func (x *Moment) Average() float64 {
	return x.Moment(1)
}

func (x *Moment) Variance() float64 {
	m1 := x.Moment(1)
	return x.Moment(2) - m1*m1
}

func (x *Moment) StdDev() float64 {
	return math.Sqrt(x.Variance())
}

func (x *Moment) Moment(k int) float64 {
	switch k {
	case 0:
		return 1
	case 1:
		return x.Sum / x.Weight
	case 2:
		return x.SumSq / x.Weight
	}
	panic("not yet supported")
}
