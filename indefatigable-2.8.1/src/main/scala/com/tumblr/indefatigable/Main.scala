package com.tumblr.indefatigable

import com.tumblr.ostrich.admin.TumblrRuntimeEnvironment
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.logging.Logger

object Main {
  val log = Logger.get(getClass)
  def main(args: Array[String]) {
    val runtime = TumblrRuntimeEnvironment(this, args)
    val service = DefaultIndefatigableConfig.thriftReceiverServiceConfig(runtime)

    try {
      service.start()
    } catch {
      case e =>
        log.error(e, "Failed to starting IndefatigableService, exiting")
        ServiceTracker.shutdown()
        System.exit(1)
    }
  }
}
