package util

import models.Asset
import play.api.Configuration

// Globally useful configurations
object AppConfig extends Config {
  val IgnoredAssets = Feature("ignoreDangerousCommands").toSet

  // Ignore asset for dangerous commands
  def ignoreAsset(tag: String): Boolean = IgnoredAssets(tag.toUpperCase)
  def ignoreAsset(asset: Asset): Boolean = ignoreAsset(asset.tag)

  val IpmiKey = "ipmi"
  def ipmi: Option[Configuration] = Config.get(IpmiKey)
  def ipmiMap = Config.toMap(IpmiKey)
}
