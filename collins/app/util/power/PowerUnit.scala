package util
package power

import models.{Asset, AssetMetaValue}
import collection.immutable.SortedSet

case class PowerUnit(config: PowerConfiguration, id: Int) extends Ordered[PowerUnit] with Iterable[PowerComponent] {
  val components: PowerComponents =
    config.components.map(componentType => PowerComponentValue(componentType, config, id))

  def component(componentType: Symbol): Option[PowerComponent] = {
    components.find(_.componentType == componentType)
  }

  override def iterator: Iterator[PowerComponent] = components.iterator
  override def compare(that: PowerUnit) = this.id - that.id
  override def equals(o: Any) = o match {
    case that: PowerUnit =>
      this.id == that.id && this.components == that.components
    case _ =>
      false
  }
  override def hashCode = components.hashCode
}

object PowerUnits extends Iterable[PowerUnit] {
  def apply(): PowerUnits = apply(None)
  def apply(cfg: Option[PowerConfiguration]): PowerUnits = apply(PowerConfiguration(cfg))
  def apply(cfg: PowerConfiguration): PowerUnits =
    apply((0 until cfg.unitsRequired).map(PowerUnit(cfg, _)))
  def apply(punits: Seq[PowerUnit]): PowerUnits = SortedSet(punits:_*)

  def iterator: Iterator[PowerUnit] = apply().iterator

  def keys(punits: PowerUnits): Set[String] =
    for (unit <- punits; component <- unit) yield(component.key)

  def unitMapFromMap(map: Map[String, Seq[String]]): Map[String,String] = unitMapFromMap(map, None)
  def unitMapFromMap(map: Map[String, Seq[String]], cfg: PowerConfiguration): Map[String,String] =
    unitMapFromMap(map, Some(cfg))
  def unitMapFromMap(map: Map[String, Seq[String]], cfg: Option[PowerConfiguration]): Map[String,String] = {
    keys(apply(cfg)).filter(k => map.contains(k) && map(k).headOption.isDefined).map { key =>
      key -> map(key).head
    }.toMap
  }
  def toMetaValues(units: PowerUnits, asset: Asset, values: Map[String,String]): Seq[AssetMetaValue] =
    (for (unit <- units; component <- unit) yield
      values.get(component.key)
        .map(AssetMetaValue(asset, component.meta, unit.id, _))
        .getOrElse(throw new Exception(component.missingData))).toSeq

}
