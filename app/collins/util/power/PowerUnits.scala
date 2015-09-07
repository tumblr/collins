package collins.util.power

import scala.collection.immutable.SortedSet
import scala.collection.mutable.HashSet

import collins.models.Asset
import collins.models.AssetMetaValue
import collins.util.power.PowerConfiguration.Messages.ValidationMissingRequired
import collins.util.power.PowerConfiguration.Messages.ValidationNonUnique

object PowerUnits extends Iterable[PowerUnit] {
  def apply(): PowerUnits = apply(None)
  def apply(cfg: Option[PowerConfiguration]): PowerUnits = apply(PowerConfiguration.get(cfg))
  def apply(cfg: PowerConfiguration): PowerUnits =
    apply((0 until cfg.unitsRequired).map(PowerUnit(cfg, _)))
  def apply(punits: Seq[PowerUnit]): PowerUnits = SortedSet(punits: _*)

  def iterator: Iterator[PowerUnit] = apply().iterator

  def keys(punits: PowerUnits): Set[String] =
    for (unit <- punits; component <- unit) yield (component.key)

  // Given a request like map, convert it to a map where keys are power component keys and values
  // are the corresponding values from the input map
  def unitMapFromMap(map: Map[String, Seq[String]]): Map[String, String] = unitMapFromMap(map, None)
  def unitMapFromMap(map: Map[String, Seq[String]], cfg: PowerConfiguration): Map[String, String] =
    unitMapFromMap(map, Some(cfg))
  def unitMapFromMap(map: Map[String, Seq[String]], cfg: Option[PowerConfiguration]): Map[String, String] = {
    keys(apply(cfg)).filter(k => map.contains(k) && map(k).headOption.isDefined).map { key =>
      key -> map(key).head
    }.toMap
  }

  // Given a map of power components and values, convert them to a sequence of meta values
  // If a value is required for a component and not found an exception is thrown
  def toMetaValues(units: PowerUnits, asset: Asset, values: Map[String, String]): Seq[AssetMetaValue] =
    (for (unit <- units; component <- unit if (component.isRequired || values.get(component.key).isDefined)) yield values.get(component.key)
      .map(AssetMetaValue(asset, component.meta, unit.id, _))
      .getOrElse(
        throw InvalidPowerConfigurationException(component.missingData, Some(component.key)))).toSeq

  // Given a map of power components and values, ensure the specified configuration is valid
  // An exception is thrown if the specified configuration is invalid
  def validateMap(values: Map[String, String]) { validateMap(values, None) }
  def validateMap(values: Map[String, String], config: Option[PowerConfiguration]) {
    validateMap(values, config.getOrElse(PowerConfiguration.get()))
  }
  def validateMap(values: Map[String, String], config: PowerConfiguration) {
    val units = apply(config)
    val tracker = new HashSet[String]()
    def seen(key: String, value: String): Boolean = {
      val normalized = "%s_%s".format(key.toUpperCase.trim, value.trim)
      if (tracker.contains(normalized))
        true
      else {
        tracker(normalized) = true
        false
      }
    }
    units.foreach { unit =>
      unit.foreach { pc => // pc == powerComponent
        val (pcRequired, pcUnique, pcKey, pcName) = (
          pc.isRequired, pc.isUnique, pc.key, pc.typeName)
        val pcValue = values.get(pcKey)
        if (pcRequired && !pcValue.isDefined)
          throw InvalidPowerConfigurationException(ValidationMissingRequired(
            pcName, pcKey), Some(pcKey))
        else if (pcRequired && pcUnique && seen(pcName, pcValue.get))
          throw InvalidPowerConfigurationException(ValidationNonUnique(
            pcName, pcValue.get), Some(pcKey))
        else if (pcRequired && pcValue.get.trim.isEmpty)
          throw InvalidPowerConfigurationException(ValidationMissingRequired(
            pcName, pcKey), Some(pcKey))
      }
    }
  }

}
