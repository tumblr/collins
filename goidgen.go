package main

import (
	"fmt"
	"net/http"
	"time"
)

var generator *TimestampGen

func main() {
	generator = NewTimestampGen(0, 6, 10)

	http.HandleFunc("/get-id", GetId)
	http.HandleFunc("/next-id", NextId)
	http.HandleFunc("/start-id", StartId)

	http.ListenAndServe(":8080", nil)
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
