package script

import (
	"bytes"
	"text/template"
)

// M is a convenience wrapper for map[string]interface{} for supplying values to templates
type M map[string]interface{}

type scriptTemplate template.Template

func parseScriptTemplate(glob string) (tmpl *scriptTemplate, err error) {
	t, err := template.ParseFiles(glob)
	return (*scriptTemplate)(t), err
}

func (x *scriptTemplate) Execute(name string, data interface{}) (s string, err error) {
	var w bytes.Buffer
	err = (*template.Template)(x).ExecuteTemplate(&w, name, data)
	return string(w.Bytes()), err
}

var scripts *scriptTemplate

func Parse(glob string) error {
	if scripts != nil {
		panic("ParseScripts can be called only once")
	}
	s, err := parseScriptTemplate(glob)
	if err != nil {
		return err
	}
	scripts = s
	return nil
}

func Execute(name string, data interface{}) (string, error) {
	return scripts.Execute(name, data)
}
