package com.tumblr.serx

import org.specs.Specification
import com.twitter.ostrich.admin._
import com.twitter.util._
import com.twitter.conversions.time._

abstract class AbstractSpec extends Specification {
  val env = RuntimeEnvironment(this, Array("-f", "config/test.scala"))
  lazy val Serx = {
	try {
    	val out = env.loadRuntimeConfig[SerxServiceServer]
		out
	} finally {
	    // You don't really want the thrift server active, particularly if you
	    // are running repetitively via ~test
	    ServiceTracker.shutdown // all services
	}
  }
}
