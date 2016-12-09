package collins.util.config

import collins.util.MessageHelper

import collins.models.Asset
import collins.models.AssetMeta

/**
 * Describes general features for collins, not tied to particular pieces of functionality
 */
object Feature extends Configurable {

  object Messages extends MessageHelper("feature") {
    def ignoreDangerousCommands(s: String) = message("ignoreDangerousCommands", s)
  }

  override val namespace = "features"
  override val referenceConfigFilename = "features_reference.conf"
  val defaultSearchResultColumns = List("TAG", "HOSTNAME", "PRIMARY_ROLE", "STATUS", "CREATED", "UPDATED")

  def allowTagUpdates = getStringSet("allowTagUpdates")
  def allowedServerUpdateStatuses = getStringSet("allowedServerUpdateStatuses").union(Set("MAINTENANCE"))
  def defaultLogType = getString("defaultLogType", "Informational").toUpperCase
  def syslogAsset = getString("syslogAsset").orElse(MultiCollinsConfig.thisInstance)
  def deleteIpmiOnDecommission = getBoolean("deleteIpmiOnDecommission", true)
  def deleteIpAddressOnDecommission = getBoolean("deleteIpAddressOnDecommission", true)
  def deleteMetaOnDecommission = getBoolean("deleteMetaOnDecommission", false)
  def useWhiteListOnRepurpose = getBoolean("useWhitelistOnRepurpose", false)
  def deleteSomeMetaOnRepurpose = getStringSet("deleteSomeMetaOnRepurpose", Set())
  def encryptedTags = getStringSet("encryptedTags")
  def keepSomeMetaOnRepurpose = getStringSet("keepSomeMetaOnRepurpose", Set())
  def intakeSupported = getBoolean("intakeSupported", true)
  def intakeIpmiOptional = getBoolean("intakeIpmiOptional", false)
  def intakeChassisTagOptional = getBoolean("intakeChassisTagOptional", false)
  def ignoreDangerousCommands = getStringSet("ignoreDangerousCommands")
  def hideMeta = getStringSet("hideMeta")
  def noLogAssets = getStringSet("noLogAssets")
  def noLogPurges = getStringSet("noLogPurges")
  def sloppyStatus = getBoolean("sloppyStatus", true)
  def sloppyTags = getBoolean("sloppyTags", false)
  def searchResultColumns = getStringList("searchResultColumns", defaultSearchResultColumns).distinct.map(_.toUpperCase)

  override protected def validateConfig() {
    defaultLogType
    encryptedTags.foreach { AssetMeta.isValidName(_) }
    syslogAsset.foreach { Asset.isValidTag(_) }
    deleteSomeMetaOnRepurpose.foreach { AssetMeta.isValidName(_) }
    keepSomeMetaOnRepurpose.foreach { AssetMeta.isValidName(_) }
    ignoreDangerousCommands.foreach { Asset.isValidTag(_) }
    hideMeta.foreach { AssetMeta.isValidName(_) }
    noLogAssets.foreach { Asset.isValidTag(_) }
    noLogPurges.foreach { AssetMeta.isValidName(_) }
    searchResultColumns.foreach { AssetMeta.isValidName(_) }

    if ((keepSomeMetaOnRepurpose & deleteSomeMetaOnRepurpose).size > 0) {
      throw new ConfigurationException(f"""Keep and delete on repurpose specify one or more of the same attribute: ${(keepSomeMetaOnRepurpose & deleteSomeMetaOnRepurpose).mkString(",")}%s""")
    }
    if (useWhiteListOnRepurpose && keepSomeMetaOnRepurpose.isEmpty) {
      throw new ConfigurationException("Keep attributes (keepSomeMetaOnRepurpose) are required when using useWhiteListOnRepurpose")
    }
    searchResultColumns
    syslogAsset
  }
}
