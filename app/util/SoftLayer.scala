package util

import config.Configurable
import models.Status
import play.api.{Play, Plugin}
import com.tumblr.play.SoftLayerPlugin

object SoftLayerConfig extends Configurable {
  override val namespace = "softlayer"
  override val referenceConfigFilename = "softlayer_reference.conf"

  def enabled = getBoolean("enabled", false)
  def username = getString("username", "")
  def password = getString("password", "")
  def allowedCancelStatus = getStringSet("allowedCancelStatus", Status.statusNames).map { s =>
    Status.Enum.withName(s).id
  }

  override protected def validateConfig() {
    if (enabled) {
      require(username.nonEmpty, "softlayer.username must not be empty if enabled")
      require(password.nonEmpty, "softlayer.password must not be empty if enabled")
      allowedCancelStatus
    }
  }
}

object SoftLayer {

  def plugin: Option[SoftLayerPlugin] = pluginEnabled

  def pluginEnabled: Option[SoftLayerPlugin] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[SoftLayerPlugin].filter(_.enabled)
    }
  }

  def pluginEnabled[T](fn: SoftLayerPlugin => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }

  def ticketLink(id: String): Option[String] = {
    pluginEnabled.flatMap { p =>
      try {
        Some(p.ticketUrl(id.toLong))
      } catch {
        case _ => None
      }
    }
  }

  def assetLink(asset: models.AssetView): Option[String] = asset match {
    case a: models.Asset => {
      pluginEnabled.flatMap { p =>
        p.softLayerUrl(asset)
      }
    }
    case _ => None
  }

  def cancelAllowed(asset: models.Asset): Boolean = {
    SoftLayerConfig.allowedCancelStatus.contains(asset.status)
  }

}
