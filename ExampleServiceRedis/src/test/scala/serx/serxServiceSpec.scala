package com.tumblr.serx

class SerxServiceSpec extends AbstractSpec {
  "SerxService" should {
	"have a #serverName" in {
		Serx.serverName must_== "SerxServer"
	}

    // TODO: Please implement your own tests.
  }
}
