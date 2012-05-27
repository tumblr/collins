package util
package power

import models.AssetMeta

/** A power related component (distribution unit, strip, outlet, etc) */
sealed trait PowerComponent extends Ordered[PowerComponent] {
  def componentType: Symbol
  def config: PowerConfiguration
  def id: Int

  def label = PowerConfiguration.Messages.ComponentLabel(name, sid)
  def meta: AssetMeta = componentType match {
    case 'STRIP => AssetMeta.findById(AssetMeta.Enum.PowerPort.id).get
    case o => AssetMeta.findOrCreateFromName(o.name)
  }
  def missingData = PowerConfiguration.Messages.MissingData(key, label)

  final def identifier: String = "POWER_%s".format(name)
  final def key: String = "%s_%s".format(identifier, sid)
  final def sid: String = config.useAlphabeticNames match {
    case true => (65 + id).toChar.toString
    case false => id.toString
  }

  override def compare(that: PowerComponent) = this.id - that.id
  override def equals(o: Any) = o match {
    case that: PowerComponent =>
      this.id == that.id && this.componentType == that.componentType
    case _ =>
      false
  }
  override def hashCode = id.hashCode + componentType.hashCode

  protected def name: String = componentType.name
}

case class PowerComponentValue(
  componentType: Symbol, config: PowerConfiguration, id: Int
) extends PowerComponent
