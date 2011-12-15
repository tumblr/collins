package models

import util.LshwRepresentation
import java.sql.Connection

object LshwHelper {
  import AssetMeta.Enum._
  def updateAsset(asset: Asset, lshw: LshwRepresentation)(implicit con: Connection): Boolean = {
    // FIXME: Need to delete specific asset meta values before accepting an update
    val mvs: Seq[AssetMetaValue] = collectCpus(asset.getId, lshw) ++
              collectMemory(asset.getId, lshw) ++
              collectNics(asset.getId, lshw) ++
              collectDisks(asset.getId, lshw)
    mvs.size == AssetMetaValue.create(mvs)
  }

  protected def collectCpus(asset_id: Long, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.cpuCount < 1) {
      return Seq()
    }
    val cpu = lshw.cpus.head
    Seq(
      AssetMetaValue(asset_id, CpuCount.id, lshw.cpuCount.toString),
      AssetMetaValue(asset_id, CpuCores.id, cpu.cores.toString),
      AssetMetaValue(asset_id, CpuThreads.id, cpu.threads.toString),
      AssetMetaValue(asset_id, CpuSpeedGhz.id, cpu.speedGhz.toString),
      AssetMetaValue(asset_id, CpuDescription.id, "%s %s".format(cpu.product, cpu.vendor))
    )
  }

  protected def collectMemory(asset_id: Long, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.memoryBanksTotal < 1) {
      return Seq()
    }
    val mem = lshw.memory.head
    Seq(
      AssetMetaValue(asset_id, MemoryAvailableBytes.id, lshw.totalMemory.inBytes.toString),
      AssetMetaValue(asset_id, MemoryBanksUsed.id, lshw.memoryBanksUsed.toString),
      AssetMetaValue(asset_id, MemoryBanksUnused.id, lshw.memoryBanksUnused.toString),
      AssetMetaValue(asset_id, MemoryDescription.id, "%s - %s %s".format(
        mem.description, mem.vendor, mem.product
      ))
    )
  }

  protected def collectNics(asset_id: Long, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.nicCount < 1) {
      return Seq()
    }
    lshw.nics.foldLeft((0,Seq[AssetMetaValue]())) { case (run,nic) =>
      val groupId = run._1
      val total = run._2
      (groupId + 1, total ++ Seq(
        AssetMetaValue(asset_id, NicSpeed.id, groupId, nic.speed.inBits.toString),
        AssetMetaValue(asset_id, MacAddress.id, groupId, nic.macAddress),
        AssetMetaValue(asset_id, NicDescription.id, groupId, "%s - %s".format(nic.product, nic.vendor))
      ))
    }._2
  }

  protected def collectDisks(asset_id: Long, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.diskCount < 1) {
      return Seq()
    }
    val physicalDisks = lshw.disks.foldLeft((0,Seq[AssetMetaValue]())) { case (run,disk) =>
      val groupId = run._1
      val total = run._2
      (groupId + 1, total ++ Seq(
        AssetMetaValue(asset_id, DiskSizeBytes.id, groupId, disk.size.inBytes.toString),
        AssetMetaValue(asset_id, DiskType.id, groupId, disk.diskType),
        AssetMetaValue(asset_id, DiskDescription.id, groupId, "%s %s".format(disk.vendor, disk.product))
      ))
    }._2
    val diskSummary = AssetMetaValue(asset_id, DiskStorageTotal.id, lshw.totalUsableStorage.inBytes.toString)
    val flashDisk = lshw.flashDisk.map { disk =>
      val groupId = physicalDisks.size
      val flashDisk = Seq(
        AssetMetaValue(asset_id, DiskIsFlash.id, groupId, "true"),
        AssetMetaValue(asset_id, DiskSizeBytes.id, groupId, disk.size.inBytes.toString),
        AssetMetaValue(asset_id, DiskType.id, groupId, "FLASH"),
        AssetMetaValue(asset_id, DiskDescription.id, groupId, "%s %s".format(disk.vendor, disk.product))
        )
      flashDisk
    }.getOrElse(Seq[AssetMetaValue]())
    Seq(diskSummary) ++ physicalDisks ++ flashDisk
  }

}
