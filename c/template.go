package c

import (
	"bytes"
	"fmt"
	"text/template"
)

func MustParse(source string) *template.Template {
	t := template.New("noname")
	return template.Must(t.Parse(source))
}

type M map[string]interface{}

func MustParseAndExecute(source string, data interface{}) string {
	t := MustParse(source)
	var w bytes.Buffer
	err := t.Execute(&w, data)
	if err != nil {
		panic(fmt.Sprintf("template execute (%s)", err))
	}
	return string(w.Bytes())
}
