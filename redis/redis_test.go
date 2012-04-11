package redis

import (
	"fmt"
)

func main() {
	c, err := Dial("localhost:6300")
	if err != nil {
		fmt.Printf("err (%s)\n", err)
		return
	}
	err = c.WriteMultiBulk("get", "chris")
	if err != nil {
		fmt.Printf("err2 (%s)\n", err)
		return
	}
	resp, err := c.ReadResponse()
	if err != nil {
		fmt.Printf("read resp (%s)\n", err)
		return
	}
	fmt.Println(ResponseString(resp))
}
