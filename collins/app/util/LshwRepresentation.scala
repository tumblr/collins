package util

sealed abstract class LshwAsset {
  val description: String
  val product: String
  val vendor: String
}
object LshwAsset {
  def apply(desc: String, prod: String, vend: String) = new LshwAsset {
    val description = desc
    val product = prod
    val vendor = vend
  }
}

case class Cpu(cores: Int, threads: Int, speedGhz: Double, description: String, product: String, vendor: String) extends LshwAsset
case class Memory(size: ByteStorageUnit, bank: Int, description: String, product: String, vendor: String) extends LshwAsset
case class Nic(speed: BitStorageUnit, macAddress: String, description: String, product: String, vendor: String) extends LshwAsset
case class Disk(size: ByteStorageUnit, diskType: String, description: String, product: String, vendor: String) extends LshwAsset
case class FlashDisk(size: ByteStorageUnit, description: String, product: String, vendor: String) extends LshwAsset

case class LshwRepresentation(
  cpus: Seq[Cpu],
  memory: Seq[Memory],
  nics: Seq[Nic],
  disks: Seq[Disk],
  flashDisk: Option[FlashDisk]
) {
  def cpuCount: Int = cpus.size
  def hasHyperthreadingEnabled: Boolean = (cpuThreadCount > cpuCoreCount)
  def cpuCoreCount: Int = cpus.foldLeft(0) { case(total,cpu) =>
    total + cpu.cores
  }
  def cpuThreadCount: Int = cpus.foldLeft(0) { case(total,cpu) =>
    total + cpu.threads
  }
  def cpuSpeed: Double = cpus.headOption.map { _.speedGhz }.getOrElse(0.0)

  def totalMemory: ByteStorageUnit = memory.foldLeft(new ByteStorageUnit(0)) { case(total,mem) =>
    new ByteStorageUnit(total.bytes + mem.size.bytes)
  }
  def memoryBanksUsed: Int = memory.filter { _.size.bytes > 0 }.size
  def memoryBanksUnused: Int = memory.filter { _.size.bytes == 0 }.size
  def memoryBanksTotal: Int = memory.size

  def totalStorage: ByteStorageUnit = disks.foldLeft(new ByteStorageUnit(0)) { case(total,disk) =>
    new ByteStorageUnit(total.bytes + disk.size.bytes)
  }
  def diskCount: Int = disks.size

  def hasFlashStorage: Boolean = flashDisk.isDefined
  def totalFlashStorage: ByteStorageUnit = flashDisk.map { _.size }.getOrElse(ByteStorageUnit(0))
  def totalUsableStorage: ByteStorageUnit = {
    val sBytes = totalStorage.bytes
    val flashBytes = flashDisk.map { fd =>
      fd.size.bytes
    }.getOrElse(0L)
    ByteStorageUnit(sBytes + flashBytes)
  }

  def nicCount: Int = nics.size
  def hasGbNic: Boolean = nics.find { _.speed.inGigabits == 1 }.map { _ => true }.getOrElse(false)
  def has10GbNic: Boolean = nics.find { _.speed.inGigabits == 10 }.map { _ => true }.getOrElse(false)
  def macAddresses: Seq[String] = nics.map { _.macAddress }

}
