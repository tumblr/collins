package com.tumblr.indefatigable

import com.tumblr.ostrich.admin.config.ServerConfig
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.tumblr.stats.TsdbStatsConfig
import com.twitter.logging.config.{FileHandlerConfig, LoggerConfig}
import com.twitter.logging.{Policy, Level}
import com.tumblr.logging.config.TumblrBasicFormatterConfig

class IndefatigableConfig
{
  var name = "Indefatigable"

  var statFilter = DefaultCollectdFilter

  var statsLogger = new TsdbStatsConfig {
    tsdHost = "127.0.0.1"
    tsdPort = 4242

    // these apply to HTTP aggregate metrics
    serviceName = "app"
    stage       = "prod"
    version     = "1"
  }

  var thriftReceiverServiceConfig = Option(new IndefatigableServiceConfig {
    loggers =
      new LoggerConfig {
        // this doens't seem to work
        level = Level.INFO
        handlers = new CollectdLogger {}
      }
  })
}

class IndefatigableServiceConfig extends ServerConfig[IndefatigableServiceServer] {
  var thriftPort: Int = 9999
  var runtime: RuntimeEnvironment = null
  
  def apply(runtime: RuntimeEnvironment) = {
    val svc = new IndefatigableServiceImpl(this)
    new IndefatigableServerImpl(this, svc)
  }
}

class DefaultIndefatigableConfig extends IndefatigableConfig
object DefaultIndefatigableConfig extends DefaultIndefatigableConfig
