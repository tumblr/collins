package util
package parsers

import scala.xml.{Elem, MalformedAttributeException, Node, NodeSeq, XML}

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
}

class LldpParser(txt: String, config: Map[String,String] = Map.empty)
  extends CommonParser[LldpRepresentation](txt, config)
{
  override def parse(): Either[Throwable,LldpRepresentation] = {
    val xml = try {
      XML.loadString(txt)
    } catch {
      case e: Throwable =>
        logger.info("Invalid XML specified: " + e.getMessage)
        return Left(e)
    }
    val rep = try {
      getInterfaces(xml).foldLeft(LldpRepresentation(Nil)) { case(holder, interface) =>
        holder.copy(interfaces = toInterface(interface) +: holder.interfaces)
      }
    } catch {
      case e: Throwable =>
        logger.warn("Caught exception processing LLDP XML: " + e.getMessage)
        return Left(e)
    }
    Right(rep.copy(interfaces = rep.interfaces.reverse))
  }

  protected def toInterface(seq: NodeSeq): Interface = {
    val name = (seq \ "@name" text)
    val chassis = findChassis(seq)
    val port = findPort(seq)
    val vlan = findVlan(seq)
    Interface(name, chassis, port, vlan)
  }

  protected def findChassis(seq: NodeSeq): Chassis = {
    val chassis = (seq \\ "chassis")
    val name = (chassis \\ "name" text)
    val id = (chassis \\ "id")
    val idType = (id \ "@type" text)
    val idValue = id.text
    val description = (chassis \\ "descr" text)
    requireNonEmpty(
      (idType -> "chassis id type"), (idValue -> "chassis id value"),
      (name -> "chassis name"), (description -> "chassis description")
    )
    Chassis(name, ChassisId(idType, idValue), description)
  }

  protected def findPort(seq: NodeSeq): Port = {
    val port = (seq \\ "port")
    val id = (port \\ "id")
    val idType = (id \ "@type" text)
    val idValue = id.text
    val description = (port \\ "descr" text)
    requireNonEmpty((idType -> "port id type"), (idValue -> "port id value"), (description -> "port description"))
    Port(PortId(idType, idValue), description)
  }

  protected def findVlan(seq: NodeSeq): Vlan = {
    val vlan = (seq \\ "vlan")
    val id = (vlan \ "@vlan-id" text)
    val name = vlan.text
    requireNonEmpty((id -> "vlan-id"), (name -> "vlan name"))
    Vlan(id.toInt, name)
  }

  protected def getInterfaces(elem: Elem): NodeSeq = {
    val elems = (elem \\ "interface").filter { node =>
      (node \ "@label" text) == "Interface"
    }
    if (elems.size < 1) {
      throw new AttributeNotFoundException("Couldn't find interface nodes in XML")
    } else {
      elems
    }
  }

  private def requireNonEmpty(seq: (String, String)*) {
    seq.foreach { i =>
      if (i._1.isEmpty) {
        throw new AttributeNotFoundException("Found empty " + i._2)
      }
    }
  }

}
