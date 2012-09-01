package util
package config

import play.api.{Configuration, Logger}

object ApplicationConfiguration {
  var globalConfig: Option[Configuration] = None
}
trait ApplicationConfiguration {
  // Handle on the play application configuration
  protected def appConfig(cfg: Option[Configuration] = None, default: Option[Configuration] = None): Configuration = cfg.orElse {
    try {
      import play.api.Play.current
      Some(current.configuration)
    } catch {
      case e: RuntimeException =>
        Logger(getClass).error("No current play application configured")
        ApplicationConfiguration.globalConfig
    }
  }.orElse(default).getOrElse(Configuration.from(Map.empty))
}
