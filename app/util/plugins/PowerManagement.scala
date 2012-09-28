package util.plugins

import collins.power.PowerAction
import collins.power.management.{PowerManagement => PowerManagementTrait, PowerManagementConfig}
import models.{Asset, AssetType, Status}
import util.config.{Configurable, ConfigValue}

import play.api.{Play, Logger}

object PowerManagement {
  protected[this] val logger = Logger(getClass)

  val PConfig = PowerManagementConfig

  def pluginEnabled: Option[PowerManagementTrait] = {
    Play.maybeApplication.flatMap { app =>
      val plugins: Seq[PowerManagementTrait] = app.plugins.filter { plugin =>
        plugin.isInstanceOf[PowerManagementTrait] && plugin.enabled
      }.map(_.asInstanceOf[PowerManagementTrait])
      plugins.size match {
        case 1 => plugins.headOption
        case n => // On case we have multiple, try and choose the one that was specified
          PowerManagementConfig.getClassOption.flatMap { klass =>
            plugins.find(_.getClass.toString.contains(klass))
          }.orElse(plugins.headOption)
      }
    }
  }

  def pluginEnabled[T](fn: PowerManagementTrait => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }

  def isPluginEnabled = pluginEnabled.isDefined

  def assetTypeAllowed(asset: Asset): Boolean = {
    val isTrue = PConfig.allowAssetTypes.contains(asset.asset_type)
    logger.debug("assetTypeAllowed: %s".format(isTrue.toString))
    isTrue
  }
  def assetStateAllowed(asset: Asset): Boolean = {
    val isFalse = !PConfig.disallowStatus.contains(asset.status)
    logger.debug("assetStateAllowed: %s".format(isFalse.toString))
    isFalse
  }
  def actionAllowed(asset: Asset, action: PowerAction): Boolean = {
    if (asset.isAllocated && PConfig.disallowWhenAllocated.contains(action)) {
      false
    } else {
      true
    }
  }

  def powerAllowed(asset: Asset): Boolean = {
    assetStateAllowed(asset) && isPluginEnabled && assetTypeAllowed(asset)
  }
}
