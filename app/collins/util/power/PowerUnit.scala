package collins.util.power

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.util.Stats

object PowerUnit {
  def apply(config: PowerConfiguration, id: Int): PowerUnit = {
    val components: PowerComponents =
      config.components.zipWithIndex.map { case(componentType, position) =>
        PowerComponentValue(componentType, config, id, position)
      }
    new PowerUnit(config, id, components)
  }
  implicit object PowerUnitFormat extends Format[PowerUnit] {
    override def reads(json: JsValue) = Stats.time("PowerUnit.Reads") {
      JsSuccess(PowerUnit(
        PowerConfiguration.get(),
        (json \ "UNIT_ID").as[Int],
        (json \ "UNITS").as[Set[PowerComponent]]
      ))
    }
    override def writes(unit: PowerUnit) = Stats.time("PowerUnit.Writes") {
      JsObject(Seq(
        "UNIT_ID" -> Json.toJson(unit.id),
        "UNITS" -> Json.toJson(unit.components.map(Json.toJson(_)))
      ))
    }
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
