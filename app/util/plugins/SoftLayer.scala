package util.plugins

import play.api.Play
import collins.softlayer.{SoftLayerConfig, SoftLayerPlugin}

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
        p.softLayerUrl(a)
      }
    }
    case _ => None
  }

  def cancelAllowed(asset: models.Asset): Boolean = {
    SoftLayerConfig.allowedCancelStatus.contains(asset.status)
  }

}
