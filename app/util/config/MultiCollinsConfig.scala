package util
package config

import models.AssetType

object MultiCollinsConfig extends Configurable {

  override val namespace = "multicollins"
  override val referenceConfigFilename = "multicollins_reference.conf"

  def enabled = getBoolean("enabled", false)
  def instanceAssetType = {
    val itype = getString("instanceAssetType")(ConfigValue.Required).map(_.trim).get
    try {
      AssetType.Enum.withName(itype)
    } catch {
      case e => 
        throw globalError("multicollins.instanceAssetType - %s is not a valid asset type".format(itype))
    }
  }
  def locationAttribute = getString("locationAttribute", "LOCATION")
  def thisInstance = getString("thisInstance", "NONE")

  override def validateConfig() {
    if (enabled) {
      instanceAssetType
    }
  }
}
