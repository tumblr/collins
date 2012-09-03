package util

import play.api.{Play, Plugin, Logger}
import com.tumblr.play.{Power, PowerAction, PowerManagement => PowerMgmt}
import models.{Asset, AssetType, Status}
import config.{Configurable, ConfigValue}

import scala.util.control.Exception.allCatch

object PowerManagement {
  protected[this] val logger = Logger(getClass)

  val PConfig = PowerManagementConfig

  def pluginEnabled: Option[PowerMgmt] = {
    Play.maybeApplication.flatMap { app =>
      val plugins: Seq[PowerMgmt] = app.plugins.filter { plugin =>
        plugin.isInstanceOf[PowerMgmt] && plugin.enabled
      }.map(_.asInstanceOf[PowerMgmt])
      plugins.size match {
        case 1 => plugins.headOption
        case n => // On case we have multiple, try and choose the one that was specified
          PowerManagementConfig.getClassOption.flatMap { klass =>
            plugins.find(_.getClass.toString.contains(klass))
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
    if (asset.getStatus().name == "Allocated" && PConfig.disallowWhenAllocated.contains(action)) {
      false
    } else {
      true
    }
  }

  def powerAllowed(asset: Asset): Boolean = {
    assetStateAllowed(asset) && isPluginEnabled && assetTypeAllowed(asset)
  }
}
