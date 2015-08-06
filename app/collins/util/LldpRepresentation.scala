package collins.util

import play.api.libs.json.Format
import play.api.libs.json.JsObject
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json

import collins.models.lldp.Interface
import collins.models.lldp.Interface.InterfaceFormat
import collins.models.lldp.LldpAttribute

object LldpRepresentation {
  import Interface._
  def empty(): LldpRepresentation = {
    new LldpRepresentation(Seq())
  }
  implicit object LldpFormat extends Format[LldpRepresentation] {
    override def reads(json: JsValue) = Stats.time("LldpRepresentation.Reads") {
        JsSuccess(LldpRepresentation(
        (json \ "INTERFACES").as[Seq[Interface]]
      ))
    }
    override def writes(lldp: LldpRepresentation) = Stats.time("LldpRepresentation.Writes") {
      JsObject(Seq(
        "INTERFACES" -> Json.toJson(lldp.interfaces)
      ))
    }
  }
}

case class LldpRepresentation(interfaces: Seq[Interface]) extends LldpAttribute {
  def chassisNames: Seq[String] = interfaces.map { _.chassis.name }
  def interfaceCount: Int = interfaces.size
  def interfaceNames: Seq[String] = interfaces.map { _.name }
  def localPorts: Seq[Int] = interfaces.map { _.portAsInt }
  def macAddresses: Seq[String] = interfaces.map { _.macAddress }
  def vlanNames: Seq[String] = interfaces.map(_.vlans.map(_.name)).flatten.distinct
  def vlanIds: Seq[Int] = interfaces.map(_.vlans.map(_.id)).flatten.distinct

  override def toJsValue() = Json.toJson(this)
  override def equals(that: Any) = that match {
    case other: LldpRepresentation =>
      (macAddresses.sorted == other.macAddresses.sorted) &&
      (chassisNames.sorted == other.chassisNames.sorted) &&
      (interfaceCount == other.interfaceCount) &&
      (interfaceNames.sorted == other.interfaceNames.sorted) &&
      (localPorts.sorted == other.localPorts.sorted) &&
      (vlanNames.sorted == other.vlanNames.sorted) &&
      (vlanIds.sorted == other.vlanIds.sorted)
    case _ => false
  }
}
