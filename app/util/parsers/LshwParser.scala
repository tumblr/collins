package util
package parsers

import models.lshw._
import config.LshwConfig
import scala.xml.{Elem, MalformedAttributeException, Node, NodeSeq, XML}

object SpeedConversions {
  private val ghz = (1000*1000*1000).toDouble
  private val mhz = (1000*1000*1000*1000).toDouble
  def hzToGhz(l: Long): Double = l/ghz
  def hzToMhz(l: Long): Double = l/mhz
}

class LshwParser(txt: String) extends CommonParser[LshwRepresentation](txt) {

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

  override def parse(): Either[Throwable,LshwRepresentation] = {
    val xml = try {
      XML.loadString(txt)
    } catch {
      case e: Throwable =>
        logger.info("Invalid XML specified: " + e.getMessage)
        return Left(e)
    }
    val rep = try {
      val base = getBaseInfo(xml)
      getCoreNodes(xml).foldLeft(LshwRepresentation(Nil,Nil,Nil,Nil,base)) { case (holder,node) =>
        matcher(node) match {
          case c: Cpu => holder.copy(cpus = c +: holder.cpus)
          case m: Memory => holder.copy(memory = m.copy(bank = holder.memory.size) +: holder.memory)
          case d: Disk => holder.copy(disks = d +: holder.disks)
          case n: Nic => holder.copy(nics = n +: holder.nics)
          case _ => holder
        }
      }
    } catch {
      case e: Throwable =>
        logger.warn("Caught exception processing LSHW XML: %s".format(e.getMessage), e)
        return Left(e)
    }
    Right(rep)
  }

  val cpuMatcher: PartialFunction[NodeSeq,Cpu] = {
    case n if isCpuNode(n) =>
      val asset = getAsset(n)
      val speedString = Option(n \ "size" text).filter(_.nonEmpty).getOrElse("0")
      val speed = SpeedConversions.hzToGhz(speedString.toLong)
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

  val diskMatcher: PartialFunction[NodeSeq,Disk] = {
    case n if (n \ "@class" text) == "disk" =>
      val _type = (n \ "physid" text).contains("\\.") match {
        case true => Disk.Type.Ide
        case false =>
          (n \ "description" text).toLowerCase.contains("cd-rom") match {
            case true => Disk.Type.CdRom
            case false => Disk.Type.Scsi
          }
      }
      val asset = getAsset(n)
      val size = (n \ "size" text) match {
        case noSize if noSize.isEmpty => ByteStorageUnit(0)
        case size => ByteStorageUnit(size.toLong)
      }
      Disk(size, _type, asset.description, asset.product, asset.vendor)
    case n if (n \ "@class" text) == "memory" && LshwConfig.flashProducts.exists(s => (n \ "product" text).toLowerCase.contains(s)) =>
      val asset = getAsset(n)
      val size = ByteStorageUnit(LshwConfig.flashSize)
      Disk(size, Disk.Type.Flash, asset.description, asset.product, asset.vendor)
  }

  private val defaultNicCapacity = LshwConfig.defaultNicCapacity

  val nicMatcher: PartialFunction[NodeSeq,Nic] = {
    case n if ((n \ "@class" text) == "network") => {
      val asset = getAsset(n)
      val mac = (n \ "serial" text)
      val speed = (n \ "capacity" text) match {
        case cap if cap.nonEmpty => BitStorageUnit(cap.toLong)
        case empty => getDefaultNicStorage(asset)
      }
      Nic(speed, mac, asset.description, asset.product, asset.vendor)
    }
  }

  /**
   * Parse the NIC capacity from product name or use the configured default
   * @return The NIC capacity of the asset or the default if the asset did not provide a NIC capacity
   * @throws AttributeNotFoundException if cannot deduce speed from product name and not default configured
   */
  private def getDefaultNicStorage(asset: LshwAsset): BitStorageUnit = {
    asset.product.toLowerCase.contains("10-gig") match {
      case true => BitStorageUnit(10000000000L)
      case false => defaultNicCapacity
        .map((s: String) => BitStorageUnit(s.toLong))
        .getOrElse(
          throw AttributeNotFoundException(
            "Could not find capacity for network interface"
        ))
    }
  }

  protected def isCpuNode(n: NodeSeq): Boolean = {
    val isProc = (n \ "@class" text) == "processor"
    // For unclear reasons, lshw B.02.12.01 on el5 can claim that
    // bogus CPUs exist. This leads to nonsensical results where lshw
    // claims there are more CPUs than physical sockets. In text mode
    // they are listed as UNCLAIMED, but are only identified in the
    // xml by empty attributes
    val hasHandle = ! (n \ "@handle" text).isEmpty
    // lshw B.02.12.01 on el5 will also populate an empty socket with
    // generic ghost data (like "I'm socket 2 and I *could* in theory
    // have a proc with this clock speed, but I'm not going to give
    // you a useful field like <empty>true</true> to figure out what's
    // going on.")  It appears that the "product" field is the best
    // bet for identifying occupied sockets.
    val isOccupiedSocket = n \ "product" nonEmpty
    val isDisabled = (n \ "@disabled" text).toString == "true"
    val probablyRealCpu = isProc && hasHandle && (LshwConfig.includeEmptySocket || isOccupiedSocket)
    if (LshwConfig.includeDisabledCpu) {
      probablyRealCpu
    } else {
      probablyRealCpu && !isDisabled
    }
  }

  protected def getCoreNodes(elem: Elem): NodeSeq = {
    val core = (elem \\ "node").find { node =>
      (node \ "@id" text) == "core"
    }.getOrElse(throw MalformedAttributeException("Expected id=core node attribute"))
    core \\ "node"
  }

  protected def getBaseInfo(elem: Elem): ServerBase = {
    // Note that this does not uniquely identify the root of the lshw
    // xml, but is only a sanity check that this is being called on
    // the correct element
    if ((elem \ "@class" text).toString == "system") {
      val asset = getAsset(elem)
      ServerBase(asset.description, asset.product, asset.vendor)
    }
    // To spice things up, sometimes we get <list>$everything</list>
    // instead of just $everything
    else if (((elem \ "node") \ "@class" text) == "system")  {
      val asset = getAsset(elem \ "node")
      ServerBase(asset.description, asset.product, asset.vendor)
    }
    else {
      throw MalformedAttributeException("Expected root class=system node attribute")
    }
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
