package util
package config

import play.api.{Configuration, Logger}

trait ApplicationConfiguration {
  // Handle on the play application configuration
  protected def appConfig(cfg: Option[Configuration] = None, default: Option[Configuration] = None): Configuration = cfg.getOrElse {
    try {
      import play.api.Play.current
      current.configuration
    } catch {
      case e: RuntimeException =>
        Logger(getClass).info("No current play application configured")
        default.getOrElse(Configuration.from(Map.empty))
    }
  }
}
