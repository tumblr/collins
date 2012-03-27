package util

import play.api.{Play, Plugin}
import com.tumblr.play.SoftLayerPlugin

object SoftLayer {

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

  def assetLink(asset: models.Asset): Option[String] = {
    pluginEnabled.flatMap { p =>
      p.softLayerUrl(asset)
    }
  }

  private[this] lazy val AllowedCancelStates: Set[Int] =
    Helpers.getConfig("softlayer")
      .flatMap(_.getString("allowedCancelStatus"))
      .getOrElse("1,2,3,4,5,6,7,8,9")
      .split(",")
      .map(_.toInt)
      .toSet

  def cancelAllowed(asset: models.Asset): Boolean = {
    AllowedCancelStates.contains(asset.status)
  }

}
