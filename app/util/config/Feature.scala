package util
package config

import models.{Asset, AssetMeta, Status}
import models.logs.LogMessageType

/**
 * Describes general features for collins, not tied to particular pieces of functionality
 */
object Feature extends Configurable {

  object Messages extends MessageHelper("feature") {
    def ignoreDangerousCommands(s: String) = message("ignoreDangerousCommands", s)
  }

  override val namespace = "features"
  override val referenceConfigFilename = "features_reference.conf"

  def allowTagUpdates = getStringSet("allowTagUpdates")
  def allowedServerUpdateStatuses = getStringSet("allowedServerUpdateStatuses").union(Set("MAINTENANCE"))
    .map { m => Status.findByName(m) }
    .filter(_.isDefined).map(_.get)
  def defaultLogType = {
    val lts = getString("defaultLogType", "Informational").toUpperCase
    try {
      LogMessageType.withName(lts)
    } catch {
      case e =>
        featureException("defaultLogType", "%s is not a valid log type".format(lts))
    }
  }
  def syslogAsset = Asset.findByTag(getString("syslogAsset", MultiCollinsConfig.thisInstance)).orElse {
    Asset.findByTag("tumblrtag1")
  }.getOrElse {
    throw globalError("neither features.syslogAsset or multicollins.thisInstance were specified")
  }
  def deleteIpmiOnDecommission = getBoolean("deleteIpmiOnDecommission", true)
  def deleteIpAddressOnDecommission = getBoolean("deleteIpAddressOnDecommission", true)
  def deleteMetaOnDecommission = getBoolean("deleteMetaOnDecommission", false)
  def useWhiteListOnRepurpose = getBoolean("useWhitelistOnRepurpose", false)
  def deleteSomeMetaOnRepurpose = getStringSet("deleteSomeMetaOnRepurpose").flatMap(AssetMeta.findByName(_))
  def encryptedTags = getStringSet("encryptedTags").flatMap(AssetMeta.findByName(_))
  def keepSomeMetaOnRepurpose = getStringSet("keepSomeMetaOnRepurpose").flatMap(AssetMeta.findByName(_))
  def intakeSupported = getBoolean("intakeSupported", true)
  def ignoreDangerousCommands = getStringSet("ignoreDangerousCommands")
  def hideMeta = getStringSet("hideMeta")
  def noLogAssets = getStringSet("noLogAssets")
  def noLogPurges = getStringSet("noLogPurges")
  def sloppyStatus = getBoolean("sloppyStatus", true)
  def sloppyTags = getBoolean("sloppyTags", false)

  protected def featureException(key: String, error: String) =
    throw new Exception("%s.%s - %s".format(namespace, key, error))

  override protected def validateConfig() {
    defaultLogType
    encryptedTags
    if (useWhiteListOnRepurpose) {
      keepSomeMetaOnRepurpose
    } else {
      deleteSomeMetaOnRepurpose
    }
    syslogAsset
  }
}
