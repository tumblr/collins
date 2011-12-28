package util

case class ChassisId(idType: String, value: String)
case class Chassis(name: String, id: ChassisId, description: String)
case class PortId(idType: String, value: String)
case class Port(id: PortId, description: String)
case class Vlan(id: Int, name: String)
case class Interface(name: String, chassis: Chassis, port: Port, vlan: Vlan)

case class LldpRepresentation(interfaces: Seq[Interface]) {
  def chassisNames: Seq[String] = interfaces.map { _.chassis.name }
  def interfaceCount: Int = interfaces.size
  def interfaceNames: Seq[String] = interfaces.map { _.name }
  def localPorts: Seq[Int] = interfaces.collect {
    case Interface(_, _, Port(PortId("local", value), _), _) => value.toInt
  }
  def macAddresses: Seq[String] = interfaces.collect {
    case Interface(_, Chassis(_, ChassisId("mac", value), _), _, _) => value
  }
  def vlanNames: Seq[String] = interfaces.map { _.vlan.name }
  def vlanIds: Seq[Int] = interfaces.map { _.vlan.id }

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
