package util
package power

import play.api.libs.json._

object PowerUnit {
  def apply(config: PowerConfiguration, id: Int): PowerUnit = {
    val components: PowerComponents =
      config.components.zipWithIndex.map { case(componentType, position) =>
        PowerComponentValue(componentType, config, id, position)
      }
    new PowerUnit(config, id, components)
  }
  implicit object PowerUnitFormat extends Format[PowerUnit] {
    import Json.toJson
    import PowerComponent._
    override def reads(json: JsValue) = PowerUnit(
      PowerConfiguration.get(),
      (json \ "UNIT_ID").as[Int],
      (json \ "UNITS").as[Set[PowerComponent]]
    )
    override def writes(unit: PowerUnit) = JsObject(Seq(
      "UNIT_ID" -> toJson(unit.id),
      "UNITS" -> toJson(unit.components.map(toJson(_)))
    ))
  }
}

case class PowerUnit(config: PowerConfiguration, id: Int, components: PowerComponents) extends Ordered[PowerUnit] with Iterable[PowerComponent] {

  def component(componentType: Symbol): Option[PowerComponent] = {
    components.find(_.componentType == componentType)
  }

  override def iterator: Iterator[PowerComponent] = components.iterator
  override def compare(that: PowerUnit) = this.id - that.id
  override def equals(o: Any) = o match {
    case that: PowerUnit =>
      this.id == that.id
    case _ =>
      false
  }
  override def hashCode = this.id.hashCode
}
