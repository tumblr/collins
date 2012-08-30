package util
package config

import models.{AssetMeta, LogMessageType}

/**
 * Describes general features for collins, not tied to particular pieces of functionality
 */
object Feature extends Configurable {

  object Messages extends MessageHelper("feature") {
    def ignoreDangerousCommands(s: String) = message("ignoreDangerousCommands", s)
  }

  val namespace = "features"
  val referenceConfigFilename = "features_reference.conf"

  def allowTagUpdates = getStringSet("allowTagUpdates")
  def defaultLogType = LogMessageType.withName(
    getString("defaultLogType").getOrElse("Informational")
  )
  def deleteIpmiOnDecommission = getBoolean("deleteIpmiOnDecommission", true)
  def deleteIpAddressOnDecommission = getBoolean("deleteIpAddressOnDecommission", true)
  def deleteMetaOnDecommission = getBoolean("deleteMetaOnDecommission", false)
  def deleteSomeMetaOnRepurpose = getStringSet("deleteSomeMetaOnRepurpose").map { m =>
    AssetMeta.findByName(m).getOrElse {
      throw new Exception("%s is not a valid attribute name".format(m))
    }
  }
  def encryptedTags = getStringSet("encryptedTags").map { m =>
    AssetMeta.findByName(m).getOrElse {
      throw new Exception("%s is not a valid attribute name".format(m))
    }
  }
  def intakeSupported = getBoolean("intakeSupported", true)
  def ignoreDangerousCommands = getStringSet("ignoreDangerousCommands")
  def hideMeta = getStringSet("hideMeta")
  def noLogAssets = getStringSet("noLogAssets")
  def noLogPurges = getStringSet("noLogPurges")
  def sloppyStatus = getBoolean("sloppyStatus", true)

  override protected def validateConfig() {
    defaultLogType
    encryptedTags
    deleteSomeMetaOnRepurpose
  }
}
