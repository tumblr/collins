package collins.util.power

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.models.AssetMeta

/** A power related component (distribution unit, strip, outlet, etc) */
sealed trait PowerComponent extends Ordered[PowerComponent] {
  def componentType: Symbol
  def config: PowerConfiguration
  def id: Int // the id of the power unit
  // the position of the component within a unit, not physical position, this has to do with ordering during display
  def position: Int 
  def value: Option[String] // value of the power component

  def label = PowerConfiguration.Messages.ComponentLabel(typeName, sid)
  def meta: AssetMeta = AssetMeta.findOrCreateFromName(identifier)

  def missingData = PowerConfiguration.Messages.MissingData(key, label)

  final def identifier: String = "POWER_%s".format(typeName)
  final def isRequired: Boolean = true
  final def isUnique: Boolean = config.uniqueComponents.contains(componentType)
  final def key: String = "%s_%s".format(identifier, sid)
  final def sid: String = config.useAlphabeticNames match {
    case true => (65 + id).toChar.toString
    case false => id.toString
  }

  override def compare(that: PowerComponent) = (this.id - that.id) match {
    case 0 => this.position compare that.position
    case n => n
  }
  override def equals(o: Any) = o match {
    case that: PowerComponent =>
      this.id == that.id && this.componentType == that.componentType
    case _ =>
      false
  }
  override def hashCode = id.hashCode + componentType.hashCode

  protected[power] def typeName: String = componentType.name
}

case class PowerComponentValue(
  componentType: Symbol,
  config: PowerConfiguration,
  id: Int,
  position: Int,
  value: Option[String] = None
) extends PowerComponent

object PowerComponent {
  private def unidentify(s: String): String = s.replaceAll("^POWER_", "")
  private def unkey(s: String): Int = PowerConfiguration.get().useAlphabeticNames match {
    case true => s.split("_").last.charAt(0).toInt - 65
    case false => s.split("_").last.charAt(0).toInt
  }
  implicit object PowerComponentFormat extends Format[PowerComponent] {
    override def reads(json: JsValue) = JsSuccess(PowerComponentValue(
      Symbol(unidentify((json \ "TYPE").as[String])),
      PowerConfiguration.get(),
      unkey((json \ "KEY").as[String]),
      (json \ "POSITION").as[Int],
      (json \ "VALUE").asOpt[String]
    ))

    override def writes(pc: PowerComponent) = JsObject(Seq(
      "KEY" -> Json.toJson(pc.key),
      "VALUE" -> Json.toJson(pc.value.getOrElse("Unspecified")),
      "TYPE" -> Json.toJson(pc.identifier),
      "LABEL" -> Json.toJson(pc.label),
      "POSITION" -> Json.toJson(pc.position),
      "IS_REQUIRED" -> Json.toJson(pc.isRequired),
      "UNIQUE" -> Json.toJson(pc.isUnique)
    ))
  }
}
