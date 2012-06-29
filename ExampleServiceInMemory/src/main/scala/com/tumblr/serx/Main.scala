package com.tumblr.serx

import com.tumblr.logging.Logging
import com.tumblr.ostrich.admin.TumblrRuntimeEnvironment
import com.twitter.ostrich.admin.{ServiceTracker}
import com.twitter.finagle.tracing.Trace

object Main extends Logging {
  def main(args: Array[String]) {
    val env = TumblrRuntimeEnvironment(this, args)
    val service = env.loadRuntimeConfig[SerxServiceServer]
    try {
      service.start()
    } catch {
      case e: Exception => {
        val msg = "Failed starting Serx, exiting"
        log.error(e, msg)
        println(msg)
        ServiceTracker.shutdown()
        System.exit(1)
      }
    }
  }
}

