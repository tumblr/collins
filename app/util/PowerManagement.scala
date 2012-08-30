package util

import play.api.{Play, Plugin, Logger}
import com.tumblr.play.{Power, PowerAction, PowerManagement => PowerMgmt}
import models.{Asset, AssetType, Status}
import config.Configurable

import scala.util.control.Exception.allCatch

object PowerManagementConfig extends Configurable {
  override val namespace = "powermanagement"
  override val referenceConfigFilename = "powermanagement_reference.conf"

  object Messages extends MessageHelper(namespace) {
    def assetStateAllowed(a: Asset) = message("disallowStatus", a.getStatus().name)
    def actionAllowed(p: PowerAction) = message("disallowWhenAllocated", p.toString)
    def assetTypeAllowed(a: Asset) = message("allowAssetTypes", a.getType().name)
  }

  def allowAssetTypes: Set[Int] = getStringSet("allowAssetTypes").map { name =>
    AssetType.findByName(name).getOrElse {
      throw new Exception("%s is not a valid asset type".format(name))
    }
  }.map(_.id)
  def disallowStatus: Set[Int] = getStringSet("disallowStatus").map { s =>
    Status.findByName(s).getOrElse {
      throw new Exception("%s is not a valid status name".format(s))
    }
  }.map(_.id)
  def disallowWhenAllocated: Set[PowerAction] = getStringSet("disallowWhenAllocated").map { p =>
    Power(p)
  }

  def enabled = getBoolean("enabled", false)
  def getClassOption = getString("class")
  def timeout = getMilliseconds("timeout").getOrElse(10000L)

  override protected def validateConfig() {
    allowAssetTypes
    disallowStatus
    disallowWhenAllocated
    enabled
    getClassOption
    timeout
  }
}

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
