package collins.util.config

import play.api.Logger
import play.api.Mode
import play.api.Play
import play.api.Play.current

import collins.models.Asset

object AppConfig {
  var globalConfig: Option[PlayConfiguration] = None 

  // Ignore asset for dangerous commands
  def ignoredAssets = Feature.ignoreDangerousCommands.map(_.toUpperCase)
  def ignoreAsset(tag: String): Boolean = ignoredAssets.contains(tag.toUpperCase)
  def ignoreAsset(asset: Asset): Boolean = ignoreAsset(asset.tag)

  def isProd() = getApplicationMode() == Mode.Prod
  def isDev() = getApplicationMode() == Mode.Dev
  def getApplicationMode(): Mode.Mode = {
    Play.maybeApplication.map { app =>
      app.mode
    }.getOrElse(Mode.Dev)
  }
}

trait AppConfig {
  // Handle on the play application configuration
  protected def appConfig(cfg: Option[PlayConfiguration] = None, default: Option[PlayConfiguration] = None): PlayConfiguration = cfg.orElse {
    try {
      import play.api.Play.current
      Some(current.configuration)
    } catch {
      case e: RuntimeException => {
        Logger(getClass).error("No current play application configured")
        AppConfig.globalConfig
      }
    }
  }.orElse(default).getOrElse(play.api.Configuration.from(Map.empty))
}
