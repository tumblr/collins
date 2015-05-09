package collins.util.config

import collins.models.AssetSort
import collins.models.AssetType

object NodeclassifierConfig extends Configurable {
  override val namespace = "nodeclassifier"
  override val referenceConfigFilename = "nodeclassifier_reference.conf"

  val SortTypes = AssetSort.values.map(_.toString)
  val DefaultSortType = AssetSort.Distance.toString

  def assetType = getString("assetType").orElse(Some("CONFIGURATION")).map { t =>
    AssetType.findByName(t) match {
      case None => throw globalError("%s is not a valid asset type".format(t))
      case Some(t) => t
    }
  }.get

  def identifyingMetaTag = getString("identifyingMetaTag", "IS_NODECLASS").toUpperCase
  def displayNameAttribute = getString("displayNameAttribute", "NAME").toUpperCase
  def excludeMetaTags = getStringSet("excludeMetaTags").map(_.toUpperCase) ++ Set(displayNameAttribute)
  def sortKeys = getStringSet("sortKeys", Set(DefaultSortType)).filter(SortTypes.contains(_))

  override protected def validateConfig() {
    assetType
    sortKeys
  }
}
