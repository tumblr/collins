package main

import (
	//"errors"
	"flag"
	"fmt"
	"log"
	"os"
	"strconv"
	"github.com/4ad/doozer"
	"tumblr/stat"
)

var (
	flagDoozerd *string = flag.String("doozerd", "", "Doozerd host:port to connect to")
	flagID      *string = flag.String("id", "", "String ID of this client")
	flagTwin    *string = flag.String("twin", "", "String ID of the other benchmark client")
	flagN       *int    = flag.Int("n", 20, "Number of benchmark iterations")
	flagK       *int    = flag.Int("k", 10, "Number of nodes to touch during an iteration")
	flagCargo   *int    = flag.Int("cargo", 2200, "Payload size for reads and writes")
)

func main() {
	flag.Parse()
	fmt.Printf("Doozer Benchmark Client\n")
	if len(*flagID) == 0  || len(*flagTwin) == 0 || *flagID == *flagTwin {
		log.Fatalf("need to specify client and twin IDs that are different")
	}
	if *flagN < 1 {
		log.Fatalf("need a positive number of iterations")
	}
	if *flagK < 1 {
		log.Fatalf("need a positive number of nodes per iteration")
	}
	if *flagCargo < 1 {
		log.Fatalf("need positive payload size")
	}

	dzr, err := doozer.Dial(*flagDoozerd)
	if err != nil {
		log.Fatalf("dial (%s)", err)
	}
	defer dzr.Close()
	if err := bench(dzr, *flagCargo, *flagID, *flagTwin, *flagN, *flagK); err != nil {
		fmt.Fprintf(os.Stderr, "Bench error (%s)\n", err)
		os.Exit(1)
	}
}

func bench(dzr *doozer.Conn, cargo int, id, twin string, n, k int) error {
	fmt.Printf("bench id=%s twin=%s n=%d k=%d\n", id, twin, n, k)
	body := make([]byte, cargo)
	var totalSampler, readSampler, syncSampler, writeSampler stat.TimeSampler

	totalSampler.Start()

	rev, err := dzr.Rev()
	if err != nil {
		fmt.Fprintf(os.Stderr, "rev (%s)\n", err)
		return err
	}

	var syncRev int64 = -1
	for i := 0; i < n; i++ {
		// Perform K writes to K different nodes
		writeSampler.Start()
		for j := 0; j < k; j++ {
			name := "/" + id + "/a" + strconv.Itoa(j)
			rev, err = dzr.Set(name, rev, body)
			if err != nil {
				fmt.Fprintf(os.Stderr, "i=%d, set %s (%s)\n", i, name, err)
				return err
			}
		}
		writeSampler.Stop()

		// Mark completion of write cycle at a special node
		rev, err = dzr.Set("/" + id + "/sync", rev, []byte(strconv.Itoa(i)))
		if err != nil {
			fmt.Fprintf(os.Stderr, "i=%d, set /%s/sync (%s)\n", i, id, err)
			return err
		}

		// Wait for twin to complete the same iteration i
		fmt.Printf("i=%d, waiting\n", i)
		syncSampler.Start()
		syncRev, err = syncOnEqual(dzr, "/" + twin + "/sync", i, syncRev)
		if err != nil {
			fmt.Fprintf(os.Stderr, "i=%d, sync /%s/sync (%s)\n", i, twin, err)
			return err
		}
		syncSampler.Stop()

		// Perform 10*k reads 
		readSampler.Start()
		for j := 0; j < 10*k; j++ {
			name := "/" + id + "/a" + strconv.Itoa(j%k)
			_, rev, err = dzr.Get(name, nil)
			if err != nil {
				fmt.Fprintf(os.Stderr, "i=%d, j=%d, get %s (%s)\n", i, j, name, err)
				return err
			}
		}
		readSampler.Stop()
	}

	totalSampler.Stop()

	fmt.Printf("doozer bench id=%s twin=%s iterations=%d files=%d\n", id, twin, n, k)
	fmt.Printf("read: %g/%g ns, sync: %g/%g ns, write: %g/%g ns, total: %g ns\n", 
		readSampler.Average(), readSampler.StdDev(),
		syncSampler.Average(), syncSampler.StdDev(),
		writeSampler.Average(), writeSampler.StdDev(),
		totalSampler.Average(),
	)
	return nil
}

func syncOnEqual(dzr *doozer.Conn, node string, value int, afterRev int64) (syncRev int64, err error) {
	var rev int64 = afterRev
	for {
		ev, err := dzr.Wait(node, rev+1)
		if err != nil {
			fmt.Fprintf(os.Stderr, "sync(%s,%d) wait error (%s)\n", node, value, err)
			return 0, err
		}
		rev = ev.Rev
		v0, err := strconv.Atoi(string(ev.Body))
		if err == nil && v0 == value {
			return rev, nil
		}
		fmt.Printf("cont'd wait on v='%s' rev=%d\n", string(ev.Body), rev)
	}
	panic("unr")
}
