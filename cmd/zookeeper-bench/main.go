package main

import (
	//"errors"
	"flag"
	"fmt"
	"log"
	"strconv"
	"time"
	"tumblr/stat"
	"tumblr/zookeeper"
)

var (
	flagZookeeper *string = flag.String("zk", "", "Comma-separated list of Zookeeper host:port servers")
	flagID        *string = flag.String("id", "", "String ID of this client")
	flagTwin      *string = flag.String("twin", "", "String ID of the other benchmark client")
	flagN         *int    = flag.Int("n", 20, "Number of benchmark iterations")
	flagK         *int    = flag.Int("k", 10, "Number of nodes to touch during an iteration")
	flagCargo     *int    = flag.Int("cargo", 2200, "Payload size for reads and writes")
)

// XXX: It would be more helpful if time sampling is per individual read/write (not in batches since they have different counts)
func main() {
	flag.Parse()
	fmt.Printf("Zookeeper benchmark tool\n")
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

	// Dial zookeeper
	zk, session, err := zookeeper.Dial(*flagZookeeper, 5*time.Second)
	if err != nil {
		log.Fatalf("dial (%s)", err)
	}
	defer zk.Close()

	// Wait for connection
	println("waiting for connection ...")
	event := <-session
	if event.State != zookeeper.STATE_CONNECTED {
		log.Fatalf("Could not connect")
	}

	if err := bench(zk, *flagCargo, *flagID, *flagTwin, *flagN, *flagK); err != nil {
		log.Fatalf("Bench error (%s)\n", err)
	}
}

var acl []zookeeper.ACL = zookeeper.WorldACL(zookeeper.PERM_ALL)

func bench(zk *zookeeper.Conn, cargo int, id, twin string, n, k int) error {
	fmt.Printf("zookeeper bench id=%s twin=%s n=%d k=%d\n", id, twin, n, k)
	body := make([]byte, cargo)
	var totalSampler, readSampler, syncSampler, writeSampler stat.TimeSampler

	totalSampler.Start()

	// Create subdirectory, sync znode and K files
	_, err := zk.Create("/" + id, "", 0, acl)
	if err != nil {
		log.Fatalf("create dir (%s)", err)
	}
	_, err = zk.Create("/" + id + "_sync", "", 0, acl)
	if err != nil {
		log.Fatalf("create sync file (%s)", err)
	}
	for j := 0; j < k; j++ {
		_, err := zk.Create("/" + id + "/a" + strconv.Itoa(j), "", 0, acl)
		if err != nil {
			log.Fatalf("create file %d (%s)", j, err)
		}
	}

	// Start benchmark iterations
	for i := 0; i < n; i++ {

		// Perform K writes to K different nodes
		for j := 0; j < k; j++ {
			name := "/" + id + "/a" + strconv.Itoa(j)
			writeSampler.Start()
			_, err := zk.Set(name, string(body), -1)
			writeSampler.Stop()
			if err != nil {
				log.Fatalf("i=%d, set %s (%s)\n", i, name, err)
			}
		}

		// Mark completion of write cycle at a special node
		_, err := zk.Set("/" + id + "_sync", strconv.Itoa(i), -1)
		if err != nil {
			log.Fatalf("i=%d, set /%s/sync (%s)\n", i, id, err)
		}

		// Wait for twin to complete the same iteration i
		fmt.Printf("i=%d, waiting\n", i)
		syncSampler.Start()
		err = syncOnEqual(zk, twin + "_sync", i)
		syncSampler.Stop()
		if err != nil {
			log.Fatalf("i=%d, sync /%s/sync (%s)\n", i, twin, err)
		}

		// Perform 10*k reads 
		for j := 0; j < 10*k; j++ {
			name := "/" + id + "/a" + strconv.Itoa(j%k)
			readSampler.Start()
			_, _, err = zk.Get(name)
			readSampler.Stop()
			if err != nil {
				log.Fatalf("i=%d, j=%d, get %s (%s)\n", i, j, name, err)
			}
		}
	}

	totalSampler.Stop()

	fmt.Printf("zookeeper bench id=%s twin=%s iterations=%d files=%d\n", id, twin, n, k)
	fmt.Printf("read: %g/%g ns, sync: %g/%g ns, write: %g/%g ns, total: %g ns\n", 
		readSampler.Average(), readSampler.StdDev(),
		syncSampler.Average(), syncSampler.StdDev(),
		writeSampler.Average(), writeSampler.StdDev(),
		totalSampler.Average(),
	)
	return nil
}

func syncOnEqual(zk *zookeeper.Conn, rootNode string, waitForValue int) error {
	// wait until node exists
	for {
		children, _, watch, err := zk.ChildrenW("/")
		if err != nil {
			log.Fatalf("children root (%s)", err)
		}
		var hasNode bool
		for _, child := range children {
			if child == rootNode {
				hasNode = true
			}
			break
		}
		if hasNode {
			break
		}
		<-watch
	}

	// wait until value desired
	for {
		data, _, watch, err := zk.GetW("/" + rootNode)
		if err != nil {
			log.Fatalf("sync(/%s,%d) wait error (%s)\n", rootNode, waitForValue, err)
		}
		value, err := strconv.Atoi(data)
		if err == nil && value == waitForValue {
			return nil
		}
		fmt.Printf("cont'd wait on value='%s'\n", string(data))
		<-watch
	}
	panic("unr")
}
