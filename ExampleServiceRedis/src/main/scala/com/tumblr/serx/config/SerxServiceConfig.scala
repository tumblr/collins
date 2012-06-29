package com.tumblr.serx
package config

import com.tumblr.ostrich.admin.config.ServerConfig
import com.twitter.ostrich.admin.RuntimeEnvironment

class SerxServiceConfig extends ServerConfig[SerxServiceServer] {
  var thriftPort: Int = 9999
  var httpPort: Int = 8888
  var runtime: RuntimeEnvironment = null

  def apply(runtime: RuntimeEnvironment) = {
    this.runtime = runtime
    val svc = new SerxServiceImpl(this)
    addServiceInfoHandler(svc)
    new SerxServerImpl(this, svc)
  }
}
