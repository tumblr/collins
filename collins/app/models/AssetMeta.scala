package models

import Model.defaults._

import util.Cache

import anorm._
import play.api.Play.current
import java.sql._

case class AssetMeta(
    id: Pk[java.lang.Long],
    name: String,
    priority: Int,
    label: String,
    description: String)
{
  require(name != null && name.toUpperCase == name && name.size > 0, "Name must be all upper case, length > 0")
  require(description != null && description.length > 0, "Need a description")
  def getId(): Long = id.get
}

object AssetMeta extends Magic[AssetMeta](Some("asset_meta")) {

  def apply(name: String, priority: Int, label: String, description: String) = {
    new AssetMeta(NotAssigned, name, priority, label, description)
  }

  override def create(am: AssetMeta)(implicit con: Connection) = {
    super.create(am) match {
      case newam =>
        Cache.invalidate("AssetMeta.findByName(%s)".format(am.name))
        newam
    }
  }

  def create(metas: Seq[AssetMeta])(implicit con: Connection): Seq[AssetMeta] = {
    metas.foldLeft(List[AssetMeta]()) { case(list, meta) =>
      if (meta.id.isDefined) throw new IllegalArgumentException("Use update, id already defined")
      AssetMeta.create(meta) +: list
    }.reverse
  }

  def findById(id: Long) = Model.withConnection { implicit con =>
    Cache.getOrElseUpdate("AssetMeta.findById(%d)".format(id)) {
      AssetMeta.find("id={id}").on('id -> id).singleOption()
    }
  }

  def findByName(name: String, con: Connection): Option[AssetMeta] = {
    implicit val c: Connection = con
    Cache.getOrElseUpdate("AssetMeta.findByName(%s)".format(name)) {
      AssetMeta.find("name={name}").on('name -> name).singleOption()
    }
  }

  def findByName(name: String): Option[AssetMeta] = Model.withConnection { con =>
    findByName(name, con)
  }

  def getViewable(): Seq[AssetMeta] = {
    // change to use stuff in Enum
    Model.withConnection { implicit connection =>
      Cache.getOrElseUpdate("AssetMeta.getViewable") {
        AssetMeta.find("priority > -1 order by priority asc").list()
      }
    }
  }

  type Enum = Enum.Value
  object Enum extends Enumeration(1) {
    val ServiceTag = Value(1, "SERVICE_TAG")
    val ChassisTag = Value(2, "CHASSIS_TAG")
    val RackPosition = Value(3, "RACK_POSITION")
    val PowerPort = Value(4, "POWER_PORT")
    //val SwitchPort = Value(5, "SWITCH_PORT") Deprecated by id LldpPortIdValue

    val CpuCount = Value(6, "CPU_COUNT")
    val CpuCores = Value(7, "CPU_CORES")
    val CpuThreads = Value(8, "CPU_THREADS")
    val CpuSpeedGhz = Value(9, "CPU_SPEED_GHZ")
    val CpuDescription = Value(10, "CPU_DESCRIPTION")

    val MemorySizeBytes = Value(11, "MEMORY_SIZE_BYTES")
    val MemoryDescription = Value(12, "MEMORY_DESCRIPTION")
    val MemorySizeTotal = Value(13, "MEMORY_SIZE_TOTAL")
    val MemoryBanksTotal = Value(14, "MEMORY_BANKS_TOTAL")

    val NicSpeed = Value(15, "NIC_SPEED") // in bits
    val MacAddress = Value(16, "MAC_ADDRESS")
    val NicDescription = Value(17, "NIC_DESCRIPTION")

    val DiskSizeBytes = Value(18, "DISK_SIZE_BYTES")
    val DiskType = Value(19, "DISK_TYPE")
    val DiskDescription = Value(20, "DISK_DESCRIPTION")
    val DiskStorageTotal = Value(21, "DISK_STORAGE_TOTAL")

    val LldpInterfaceName = Value(22, "LLDP_INTERFACE_NAME")
    val LldpChassisName = Value(23, "LLDP_CHASSIS_NAME")
    val LldpChassisIdType = Value(24, "LLDP_CHASSIS_ID_TYPE")
    val LldpChassisIdValue = Value(25, "LLDP_CHASSIS_ID_VALUE")
    val LldpChassisDescription = Value(26, "LLDP_CHASSIS_DESCRIPTION")
    val LldpPortIdType = Value(27, "LLDP_PORT_ID_TYPE")
    val LldpPortIdValue = Value(28, "LLDP_PORT_ID_VALUE")
    val LldpPortDescription = Value(29, "LLDP_PORT_DESCRIPTION")
    val LldpVlanId = Value(30, "LLDP_VLAN_ID")
    val LldpVlanName = Value(31, "LLDP_VLAN_NAME")

    val NicName = Value(32, "INTERFACE_NAME")
    val NicAddress = Value(33, "INTERFACE_ADDRESS")
  }
}


