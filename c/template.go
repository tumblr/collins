package c

import (
	"bytes"
	"fmt"
	"text/template"
)

func MustParse(source string) *template.Template {
	t := template.New("_commander_").Funcs(funcMap)
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

var funcMap = template.FuncMap{
	"printCommaSeparated": printCommaSeparated,
}

func printCommaSeparated(x interface{}) string {
	slice := x.([]string)
	var w bytes.Buffer
	for i, v := range slice {
		w.WriteString(v)
		if i < len(slice)-1 {
			w.WriteByte(byte(','))
		}
	}
	return string(w.Bytes())
}
