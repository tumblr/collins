package main

import (
	"flag"
	"fmt"
	"log"
	"net/http"
	"time"
)

import _ "net/http/pprof"

var (
	listenAddr = flag.String("http", ":8080", "http listen address")
)

var generator *TimestampGen

func main() {
	flag.Parse()

	generator = NewTimestampGen(0, 6, 10)

	http.HandleFunc("/get-id", GetId)
	http.HandleFunc("/next-id", NextId)
	http.HandleFunc("/start-id", StartId)

	log.Fatal(http.ListenAndServe(*listenAddr, nil))
}

func GetId(w http.ResponseWriter, r *http.Request) {
	fmt.Fprintf(w, "%d", generator.GetId())
}

func NextId(w http.ResponseWriter, r *http.Request) {
	id, err := generator.NextId(time.Now().UnixNano())
	if err != nil {
		http.Error(w, err.Error(), 502)
		return
	}

	fmt.Fprintf(w, "%d", id)
}

func StartId(w http.ResponseWriter, r *http.Request) {
	fmt.Fprintf(w, "%d", generator.StartId())
}
