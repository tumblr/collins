package util

import play.api.{Play, Plugin, Logger}
import com.tumblr.play.{PowerManagement => PowerMgmt}
import models.{AssetType, Status}

trait PowerManagementConfig extends Config {
  lazy val DisallowedAssetStates: Set[Int] = Config.statusAsSet(
    "powermanagement", "disallowStatus", Status.statusNames.mkString(",")
  )

  lazy val AllowedAssetTypes: Set[Int] =
    getString("powermanagement","allowAssetTypes","SERVER_NODE")
      .split(",").clean
      .map(name => AssetType.Enum.withName(name).id)
      .toSet;
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

  def powerAllowed(asset: models.Asset): Boolean = {
    val assetStateAllowed = !DisallowedAssetStates.contains(asset.status)
    val pluginIsEnabled = isPluginEnabled
    val assetTypeAllowed = AllowedAssetTypes.contains(asset.asset_type)
    val allowed = assetStateAllowed &&
                  pluginIsEnabled &&
                  assetTypeAllowed;
    logger.debug("AssetState allowed? " + assetStateAllowed)
    logger.debug("Plugin enabled? " + pluginIsEnabled)
    logger.debug("AssetType allowed? " + assetTypeAllowed)
    allowed
  }
}
