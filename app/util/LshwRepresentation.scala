package util

import models.lshw._
import play.api.libs.json._

object LshwRepresentation {
  def empty(): LshwRepresentation = {
    new LshwRepresentation(Seq(), Seq(), Seq(), Seq(), null)
  }
  implicit object LshwFormat extends Format[LshwRepresentation] {
    import Cpu._
    import Disk._
    import Memory._
    import Nic._
    import ServerBase._
    import Json.toJson
    override def reads(json: JsValue) = LshwRepresentation(
      (json \ "CPU").as[Seq[Cpu]],
      (json \ "MEMORY").as[Seq[Memory]],
      (json \ "NIC").as[Seq[Nic]],
      (json \ "DISK").as[Seq[Disk]],
      (json \ "BASE").as[ServerBase]
    )
    override def writes(lshw: LshwRepresentation) = JsObject(Seq(
      "CPU" -> toJson(lshw.cpus),
      "MEMORY" -> toJson(lshw.memory),
      "NIC" -> toJson(lshw.nics),
      "DISK" -> toJson(lshw.disks),
      "BASE" -> toJson(lshw.base)
    ))
  }
}

case class LshwRepresentation(
  cpus: Seq[Cpu],
  memory: Seq[Memory],
  nics: Seq[Nic],
  disks: Seq[Disk],
  base: ServerBase

) {
  def cpuCount: Int = cpus.size
  def hasHyperthreadingEnabled: Boolean = (cpuThreadCount > cpuCoreCount)
  def cpuCoreCount: Int = cpus.foldLeft(0) { case(total,cpu) =>
    total + cpu.cores
  }
  def cpuThreadCount: Int = cpus.foldLeft(0) { case(total,cpu) =>
    total + cpu.threads
  }
  def cpuSpeed: Double = cpus.sortBy(_.speedGhz).lastOption.map(_.speedGhz).getOrElse(0.0)

  def totalMemory: ByteStorageUnit = memory.foldLeft(new ByteStorageUnit(0)) { case(total,mem) =>
    new ByteStorageUnit(total.bytes + mem.size.bytes)
  }
  def memoryBanksUsed: Int = memory.filter { _.size.bytes > 0 }.size
  def memoryBanksUnused: Int = memory.filter { _.size.bytes == 0 }.size
  def memoryBanksTotal: Int = memory.size

  def totalStorage: ByteStorageUnit = disks.filterNot { disk =>
    disk.isFlash
  }.foldLeft(new ByteStorageUnit(0)) { case(total,disk) =>
    new ByteStorageUnit(total.bytes + disk.size.bytes)
  }
  def diskCount: Int = disks.size

  def hasCdRom: Boolean = disks.find { _.isCdRom }.isDefined
  def hasFlashStorage: Boolean = disks.find { _.isFlash }.isDefined
  def totalFlashStorage: ByteStorageUnit = {
    disks.filter(_.isFlash).foldLeft(ByteStorageUnit(0)) { case (sum,disk) =>
      new ByteStorageUnit(sum.bytes + disk.size.bytes)
    }
  }
  def totalUsableStorage: ByteStorageUnit = disks.foldLeft(ByteStorageUnit(0)) { case (sum,disk) =>
    new ByteStorageUnit(sum.bytes + disk.size.bytes)
  }

  def nicCount: Int = nics.size
  def hasGbNic: Boolean = nics.find { _.speed.inGigabits == 1 }.map { _ => true }.getOrElse(false)
  def has10GbNic: Boolean = nics.find { _.speed.inGigabits == 10 }.map { _ => true }.getOrElse(false)
  def macAddresses: Seq[String] = nics.map { _.macAddress }

  def toJsValue() = Json.toJson(this)

  override def equals(that: Any) = that match {
    case other: LshwRepresentation =>
      (macAddresses.sorted == other.macAddresses.sorted) &&
      (cpuCount == other.cpuCount) &&
      (hasHyperthreadingEnabled == other.hasHyperthreadingEnabled) &&
      (cpuCoreCount == other.cpuCoreCount) &&
      (cpuThreadCount == other.cpuThreadCount) &&
      (cpuSpeed == other.cpuSpeed) &&
      (totalMemory.inBytes == other.totalMemory.inBytes) &&
      (memoryBanksUsed == other.memoryBanksUsed) &&
      (memoryBanksUnused == other.memoryBanksUnused) &&
      (memoryBanksTotal == other.memoryBanksTotal) &&
      (totalStorage.inBytes == other.totalStorage.inBytes) &&
      (diskCount == other.diskCount) &&
      (hasFlashStorage == other.hasFlashStorage) &&
      (totalFlashStorage.inBytes == other.totalFlashStorage.inBytes) &&
      (totalUsableStorage.inBytes == other.totalUsableStorage.inBytes) &&
      (nicCount == other.nicCount) &&
      (hasGbNic == other.hasGbNic) &&
      (has10GbNic == other.has10GbNic)
    case _ => false
  }
}
