package util

import play.api.{Play, Plugin, Logger}
import com.tumblr.play.{Power, PowerAction, PowerManagement => PowerMgmt}
import models.{Asset, AssetType, Status}

trait PowerManagementConfig extends Config {
  val ConfigKey = "powermanagement"
  lazy val DisallowedAssetStates: Set[Int] = Config.statusAsSet(
    ConfigKey, "disallowStatus", Status.statusNames.mkString(",")
  )

  lazy val DisallowedWhenAllocated: Set[PowerAction] =
    getString(ConfigKey, "disallowWhenAllocated", "")
      .split(",").clean.toSet.map { a => Power(a) }

  lazy val AllowedAssetTypes: Set[Int] =
    getString(ConfigKey,"allowAssetTypes","SERVER_NODE")
      .split(",").clean
      .map(name => AssetType.Enum.withName(name).id)
      .toSet;

  object Messages extends MessageHelper(ConfigKey) {
    def assetStateAllowed(a: Asset) = message("disallowStatus", a.getStatus().name)
    def actionAllowed(p: PowerAction) = message("disallowWhenAllocated", p.toString)
    def assetTypeAllowed(a: Asset) = message("allowAssetTypes", a.getType().name)
  }
}

object PowerManagementConfig extends PowerManagementConfig

object PowerManagement extends PowerManagementConfig {
  protected[this] val logger = Logger(getClass)

  def pluginEnabled: Option[PowerMgmt] = {
    Play.maybeApplication.flatMap { app =>
      val plugins: Seq[PowerMgmt] = app.plugins.filter { plugin =>
        plugin.isInstanceOf[PowerMgmt] && plugin.enabled
      }.map(_.asInstanceOf[PowerMgmt])
      plugins.size match {
        case 1 => plugins.headOption
        case n => // On case we have multiple, try and choose the one that was specified
          app.configuration.getConfig("powermanagement").flatMap { cfg =>
            cfg.getString("class").flatMap { klass =>
              plugins.find(_.getClass.toString.contains(klass)) // Option[PowerMgmt]
            }
          }.orElse(plugins.headOption)
      }
    }
  }

  def pluginEnabled[T](fn: PowerMgmt => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }

  def isPluginEnabled = pluginEnabled.isDefined

  def assetTypeAllowed(asset: Asset): Boolean = AllowedAssetTypes.contains(asset.asset_type)
  def assetStateAllowed(asset: Asset): Boolean = !DisallowedAssetStates.contains(asset.status)
  def actionAllowed(asset: Asset, action: PowerAction): Boolean = {
    if (asset.getStatus().name == "Allocated" && DisallowedWhenAllocated.contains(action)) {
      false
    } else {
      true
    }
  }

  def powerAllowed(asset: Asset): Boolean = {
    assetStateAllowed(asset) && isPluginEnabled && assetTypeAllowed(asset)
  }
}
