package util

import play.api.{Play, Plugin}
import com.tumblr.play.{PowerManagement => PowerMgmt}
import models.{AssetType, Status}

trait PowerManagementConfig extends Config {
  lazy val DisallowedPowerStates: Set[Int] = Config.statusAsSet(
    "powermanagement", "disallowStatus", Status.statusNames.mkString(",")
  )

  lazy val AllowedAssetTypes: Set[Int] =
    getString("powermanagement","allowAssetTypes","SERVER_NODE")
      .split(",").clean
      .map(name => AssetType.findByName(name).map(_.id).getOrElse(-1))
      .toSet;
}

object PowerManagementConfig extends PowerManagementConfig

object PowerManagement extends PowerManagementConfig {
  def pluginEnabled: Option[PowerMgmt] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[PowerMgmt].filter(_.enabled)
    }
  }

  def pluginEnabled[T](fn: PowerMgmt => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }

  def isPluginEnabled = pluginEnabled.isDefined

  def powerAllowed(asset: models.Asset): Boolean = {
    !DisallowedPowerStates.contains(asset.status) && isPluginEnabled && AllowedAssetTypes.contains(asset.asset_type)
  }
}
