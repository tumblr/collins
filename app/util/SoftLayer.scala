package util

import models.Status
import play.api.{Play, Plugin}
import com.tumblr.play.SoftLayerPlugin

object SoftLayerConfig {
  lazy val AllowedCancelStates: Set[Int] =
    Config.statusAsSet(
      "softlayer", "allowedCancelStatus", Status.statusNames.mkString(",")
  )
}

object SoftLayer {

  import SoftLayerConfig._

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
    AllowedCancelStates.contains(asset.status)
  }

}
