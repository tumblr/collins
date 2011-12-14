package util

sealed trait LshwAsset {
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
case class Nic(speed: BitStorageUnit, description: String, product: String, vendor: String) extends LshwAsset
case class Disk(size: ByteStorageUnit, diskType: String, description: String, product: String, vendor: String) extends LshwAsset

case class LshwRepresentation(
  cpus: Seq[Cpu],
  memory: Seq[Memory],
  nics: Seq[Nic],
  disks: Seq[Disk]
) {
  def cpuCount = cpus.size
  def hasHypthreadingEnabled = (cpuThreadCount > cpuCoreCount)
  def cpuCoreCount = cpus.foldLeft(0) { case(total,cpu) =>
    total + cpu.cores
  }
  def cpuThreadCount = cpus.foldLeft(0) { case(total,cpu) =>
    total + cpu.threads
  }
  def cpuSpeed = cpus.headOption.map { _.speedGhz }.getOrElse(0.0)

  def totalMemory = memory.foldLeft(new ByteStorageUnit(0)) { case(total,mem) =>
    new ByteStorageUnit(total.bytes + mem.size.bytes)
  }
  def memoryBanksUsed = memory.filter { _.size.bytes > 0 }.size
  def memoryBanksUnused = memory.filter { _.size.bytes == 0 }.size
  def memoryBanksTotal = memory.size

  def totalStorage = disks.foldLeft(new ByteStorageUnit(0)) { case(total,disk) =>
    new ByteStorageUnit(total.bytes + disk.size.bytes)
  }
  def diskCount = disks.size

  def nicCount = nics.size
  def hasGbNic = nics.find { _.speed.inGigabits == 1 }.map { _ => true }.getOrElse(false)
  def has10GbNic = nics.find { _.speed.inGigabits == 10 }.map { _ => true }.getOrElse(false)

}
