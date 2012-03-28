package util

import play.api.{Play, Plugin}
import com.tumblr.play.{PowerManagement => PowerMgmt}

object PowerManagement {
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

  private[this] lazy val DisallowedPowerStates: Set[Int] =
    Helpers.getConfig("powermanagement")
      .flatMap(_.getString("disallowStatus"))
      .getOrElse("1,2,3,4,5,6,7,8,9,10")
      .split(",")
      .map(_.toInt)
      .toSet

  private[this] lazy val AllowedAssetTypes: Set[Int] =
    Helpers.getConfig("powermanagement")
      .flatMap(_.getString("allowAssetTypes"))
      .getOrElse("1")
      .split(",")
      .map(_.toInt)
      .toSet

  def powerAllowed(asset: models.Asset): Boolean = {
    !DisallowedPowerStates.contains(asset.status) && isPluginEnabled && AllowedAssetTypes.contains(asset.asset_type)
  }
}
