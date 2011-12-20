package util

import play.api._

import scala.xml.{Elem, MalformedAttributeException, Node, NodeSeq, XML}

case class AttributeNotFoundException(msg: String) extends Exception(msg)
object SpeedConversions {
  private val ghz = (1000*1000*1000).toDouble
  private val mhz = (1000*1000*1000*1000).toDouble
  def hzToGhz(l: Long): Double = l/ghz
  def hzToMhz(l: Long): Double = l/mhz
}

class LshwParser(txt: String, config: Map[String,String] = Map.empty) {
  require(txt != null && txt.length > 0, "LshwParser can not parse empty files")
  private[this] val logger = Logger.logger

  val wildcard: PartialFunction[NodeSeq,LshwAsset] = { case _ => null }
  lazy val matcher = cpuMatcher.orElse(
    memMatcher.orElse(
      diskMatcher.orElse(
        nicMatcher.orElse(
          wildcard
        )
      )
    )
  )

  def parse(): Either[Throwable,LshwRepresentation] = {
    val xml = try {
      XML.loadString(txt)
    } catch {
      case e: Throwable =>
        logger.info("Invalid XML specified: " + e.getMessage)
        return Left(e)
    }
    val rep = try {
      getCoreNodes(xml).foldLeft(LshwRepresentation(Nil,Nil,Nil,Nil)) { case (holder,node) =>
        matcher(node) match {
          case c: Cpu => holder.copy(cpus = c +: holder.cpus)
          case m: Memory => holder.copy(memory = m +: holder.memory)
          case d: Disk => holder.copy(disks = d +: holder.disks)
          case n: Nic => holder.copy(nics = n +: holder.nics)
          case _ => holder
        }
      }
    } catch {
      case e: Throwable =>
        logger.warn("Caught exception processing LSHW XML: " + e.getMessage)
        e.printStackTrace()
        return Left(e)
    }
    Right(rep)
  }

  val cpuMatcher: PartialFunction[NodeSeq,Cpu] = {
    case n if (n \ "@class" text) == "processor" =>
      val asset = getAsset(n)
      val speed = SpeedConversions.hzToGhz((n \ "size" text).toLong)
      val map = {
        val settings = (n \ "configuration" \ "setting")
        settings.size match {
          case 0 => Map("cores" -> "1", "threads" -> "1")
          case _ => settingsMap(settings)
        }
      }
      val cores = map.getOrElse("cores", {
        throw AttributeNotFoundException("Could not find cpu configuration.setting.cores")
      }).toInt
      val threads = map.getOrElse("threads", {
        throw AttributeNotFoundException("Could not find cpu configuration.setting.threads")
      }).toInt
      Cpu(cores, threads, speed, asset.description, asset.product, asset.vendor)
  }

  val memMatcher: PartialFunction[NodeSeq,Memory] = {
    case n if (n \ "@class" text) == "memory" && (n \ "@id" text).contains("bank:") =>
      val asset = getAsset(n)
      val size = (n \ "size" text) match {
        case n if n.isEmpty => ByteStorageUnit(0)
        case n => ByteStorageUnit(n.toLong)
      }
      val bank: Int = try { (n \ "@id" text).split(":").last.toInt } catch { case _ => -1 }
      Memory(size, bank, asset.description, asset.product, asset.vendor)
  }

  // NOTE There is no way to tell how large the virident cards are so we used a default
  val flashDescription = config.getOrElse("flashDescription", "flash")
  val flashSize = config.getOrElse("flashSize", "1400000000000").toLong
  val diskMatcher: PartialFunction[NodeSeq,Disk] = {
    case n if (n \ "@class" text) == "disk" =>
      val _type = (n \ "physid" text).contains("\\.") match {
        case true => Disk.Type.Ide
        case false => Disk.Type.Scsi
      }
      val asset = getAsset(n)
      val size = (n \ "size" text) match {
        case noSize if noSize.isEmpty => ByteStorageUnit(0)
        case size => ByteStorageUnit(size.toLong)
      }
      Disk(size, _type, asset.description, asset.product, asset.vendor)
    case n if (n \ "@class" text) == "memory" && (n \ "description" text).toLowerCase.contains(flashDescription) =>
      val asset = getAsset(n)
      val size = ByteStorageUnit(flashSize)
      Disk(size, Disk.Type.Flash, asset.description, asset.product, asset.vendor)
  }

  val nicMatcher: PartialFunction[NodeSeq,Nic] = {
    case n if (n \ "@class" text) == "network" =>
      val asset = getAsset(n)
      val mac = (n \ "serial" text)
      val speed = (n \ "capacity" text) match {
        case cap if cap.nonEmpty => BitStorageUnit(cap.toLong)
        case empty => asset.product.toLowerCase.contains("10-gig") match {
          case true => BitStorageUnit(10000000000L)
          case false =>
            throw AttributeNotFoundException(
              "Could not find capacity for network interface"
            )
        }
      }
      Nic(speed, mac, asset.description, asset.product, asset.vendor)
  }

  protected def getCoreNodes(elem: Elem): NodeSeq = {
    val core = (elem \ "node").find { node =>
      (node \ "@id" text) == "core"
    }.getOrElse(throw MalformedAttributeException("Expected id=core node attribute"))
    core \\ "node"
  }

  protected def settingsMap(n: NodeSeq): Map[String,String] = {
    n.foldLeft(Map[String,String]()) { case(r,setting) =>
      r ++ Map((setting \ "@id" text) -> (setting \ "@value" text))
    }
  }

  protected def getAsset(node: NodeSeq): LshwAsset = {
    val description = (node \ "description" text)
    val product = (node \ "product" text)
    val vendor = (node \ "vendor" text)
    LshwAsset(description, product, vendor)
  }

}
