package util
package config

import models.AssetType
import models.AssetSortType

object NodeclassifierConfig extends Configurable {
  override val namespace = "nodeclassifier"
  override val referenceConfigFilename = "nodeclassifier_reference.conf"

  val SortTypes = AssetSortType.values.map(_.toString)
  val DefaultSortType = AssetSortType.Distance.toString

  def assetType = getString("assetType").orElse(Some("CONFIGURATION")).flatMap { t =>
    try {
      Option(AssetType.Enum.withName(t))
    } catch {
      case e =>
        logger.warn("nodeclassifier.assetType - %s is not a valid asset type".format(t))
        None
    }
  }.getOrElse(AssetType.Enum.Config)

  def identifyingMetaTag = getString("identifyingMetaTag", "IS_NODECLASS").toUpperCase
  def excludeMetaTags = getStringSet("excludeMetaTags").map(_.toUpperCase)
  def sortKeys = getString("sortKeys", SortTypes).getOrElse(DefaultSortType)

  override protected def validateConfig() {
    assetType
    sortKeys
  }
}
