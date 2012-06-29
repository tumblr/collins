package util
package parsers

import scala.xml.{Elem, MalformedAttributeException, Node, NodeSeq, XML}

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
    val vlans = findVlans(seq)
    Interface(name, chassis, port, vlans)
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

  protected def findVlans(seq: NodeSeq): Seq[Vlan] = {
    (seq \\ "vlan").foldLeft(Seq[Vlan]()) { case(vseq, vlan) =>
      val id = Option(vlan \ "@vlan-id" text).filter(_.nonEmpty).getOrElse("0")
      val name = vlan.text
      requireNonEmpty((id -> "vlan-id"), (name -> "vlan name"))
      Vlan(id.toInt, name) +: vseq
    }
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
