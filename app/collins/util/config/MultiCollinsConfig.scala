package collins.util.config

import collins.models.AssetType

object MultiCollinsConfig extends Configurable {

  override val namespace = "multicollins"
  override val referenceConfigFilename = "multicollins_reference.conf"

  def enabled = getBoolean("enabled", false)
  def instanceAssetType = {
    val itype = getString("instanceAssetType")(ConfigValue.Required).map(_.trim).get
    AssetType.findByName(itype) match {
      case Some(atype) => atype
      case None =>
        throw globalError("multicollins.instanceAssetType - %s is not a valid asset type".format(itype))
    }
  }
  def locationAttribute = getString("locationAttribute", "LOCATION")
  def thisInstance = getString("thisInstance")
  def queryCacheTimeout = getInt("queryCacheTimeout", 30)
  def cacheEnabled = getBoolean("cacheEnabled", true)

  override def validateConfig() {
    if (enabled) {
      instanceAssetType
    }
  }
}
