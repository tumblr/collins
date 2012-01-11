package util

import play.api.libs.json._

case class ChassisId(idType: String, value: String)
case class Chassis(name: String, id: ChassisId, description: String) {
  def macAddress: String = id.value
  def forJsonObject(): Seq[(String,JsValue)] = {
    Seq(
      "NAME" -> JsString(name),
      "ID_TYPE" -> JsString(id.idType),
      "ID_VALUE" -> JsString(id.value),
      "DESCRIPTION" -> JsString(description)
    )
  }
}
case class PortId(idType: String, value: String)
case class Port(id: PortId, description: String) {
  def isLocal: Boolean = id.idType == "local"
  def toInt: Int = id.value.toInt

  def forJsonObject(): Seq[(String,JsValue)] = {
    Seq(
      "ID_TYPE" -> JsString(id.idType),
      "ID_VALUE" -> JsString(id.value),
      "DESCRIPTION" -> JsString(description)
    )
  }
}
case class Vlan(id: Int, name: String) {
  def forJsonObject(): Seq[(String,JsValue)] = {
    Seq(
      "ID" -> JsNumber(id),
      "NAME" -> JsString(name)
    )
  }
}
case class Interface(name: String, chassis: Chassis, port: Port, vlan: Vlan) {
  def isPortLocal: Boolean = port.isLocal
  def portAsInt: Int = port.toInt
  def macAddress: String = chassis.macAddress
  def forJsonObject(): Seq[(String,JsValue)] = {
    Seq(
      "NAME" -> JsString(name),
      "CHASSIS" -> JsObject(chassis.forJsonObject),
      "PORT" -> JsObject(port.forJsonObject),
      "VLAN" -> JsObject(vlan.forJsonObject)
    )
  }
}

case class LldpRepresentation(interfaces: Seq[Interface]) {
  def chassisNames: Seq[String] = interfaces.map { _.chassis.name }
  def interfaceCount: Int = interfaces.size
  def interfaceNames: Seq[String] = interfaces.map { _.name }
  def localPorts: Seq[Int] = interfaces.map { _.portAsInt }
  def macAddresses: Seq[String] = interfaces.map { _.macAddress }
  def vlanNames: Seq[String] = interfaces.map { _.vlan.name }
  def vlanIds: Seq[Int] = interfaces.map { _.vlan.id }

  def forJsonObject(): Seq[(String,JsValue)] = Seq(
    "INTERFACES" -> JsArray(interfaces.map { i => JsObject(i.forJsonObject) }.toList)
  )
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
