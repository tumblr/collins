package models

import util._
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

  def reconstruct(asset: Asset): Tuple2[LshwRepresentation,Seq[MetaWrapper]] = {
    val assetMeta = AssetMetaValue.findAllByAssetId(asset.getId)
    val metaMap = assetMeta.groupBy { _.getGroupId }
    val cpus = reconstructCpu(metaMap)
    val memory = reconstructMemory(metaMap)
    val nics = reconstructNics(metaMap)
    val disks = reconstructDisks(metaMap).groupBy { _.isInstanceOf[FlashDisk] }
    val spinningDisks = disks.get(false).getOrElse(Nil).asInstanceOf[Seq[Disk]]
    val flashDisk = disks.get(true).flatMap { _.headOption }.asInstanceOf[Option[FlashDisk]]
    (LshwRepresentation(cpus, memory, nics, spinningDisks, flashDisk), assetMeta)
  }

  protected def reconstructCpu(meta: Map[Int, Seq[MetaWrapper]]): Seq[Cpu] = {
    meta.get(0).map { seq =>
      val cpuCount = finder(seq, CpuCount, _.toInt, 0)
      val cpuCores = finder(seq, CpuCores, _.toInt, 0)
      val cpuThreads = finder(seq, CpuThreads, _.toInt, 0)
      val cpuSpeed = finder(seq, CpuSpeedGhz, _.toDouble, 0.0)
      val cpuDescription = finder(seq, CpuDescription, _.toString, "")
      (0 until cpuCount).map { id =>
        Cpu(cpuCores, cpuThreads, cpuSpeed, cpuDescription, "", "")
      }.toSeq
    }.getOrElse(Nil)
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

  private def finder[T](m: Seq[MetaWrapper], e: AssetMeta.Enum, c: (String => T), d: T): T = {
    m.find { _.getMetaId == e.id }.map { i => c(i.getValue) }.getOrElse(d)
  }

  protected def reconstructMemory(meta: Map[Int, Seq[MetaWrapper]]): Seq[Memory] = {
    meta.get(0).map { seq =>
      val memAvail = finder(seq, MemoryAvailableBytes, _.toLong, 0L)
      val banksUsed = finder(seq, MemoryBanksUsed, _.toInt, 0)
      val banksUnused = finder(seq, MemoryBanksUnused, _.toInt, 0)
      val totalBanks = banksUsed + banksUnused
      val description = finder(seq, MemoryDescription, _.toString, "")
      val mem = (banksUsed > 0) match {
        case true => memAvail / banksUsed
        case false => memAvail
      } 
      (0 until totalBanks).map { bank =>
        if (bank < banksUsed) {
          Memory(ByteStorageUnit(mem), bank, description, "", "")
        } else {
          Memory(ByteStorageUnit(0), bank, "Empty Memory Bank", "", "")
        }
      }.toSeq
    }.getOrElse(Nil)
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

  protected def reconstructNics(meta: Map[Int, Seq[MetaWrapper]]): Seq[Nic] = {
    meta.foldLeft(Seq[Nic]()) { case (seq, map) =>
      val groupId = map._1
      val wrapSeq = map._2
      val nicSpeed = finder(wrapSeq, NicSpeed, _.toLong, 0L)
      val macAddress = finder(wrapSeq, MacAddress, _.toString, "")
      val descr = finder(wrapSeq, NicDescription, _.toString, "")
      if (nicSpeed == 0L && macAddress.isEmpty && descr.isEmpty) {
        seq
      } else {
        Nic(BitStorageUnit(nicSpeed), macAddress, descr, "", "") +: seq
      }
    }
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

  protected def reconstructDisks(meta: Map[Int, Seq[MetaWrapper]]): Seq[LshwAsset] = {
    meta.foldLeft(Seq[LshwAsset]()) { case (seq, map) =>
      val groupId = map._1
      val wrapSeq = map._2
      val diskSize = finder(wrapSeq, DiskSizeBytes, _.toLong, 0L)
      val diskType = finder(wrapSeq, DiskType, _.toString, "")
      val descr = finder(wrapSeq, DiskDescription, _.toString, "")
      val isFlash = finder(wrapSeq, DiskIsFlash, _.toBoolean, false)
      if (diskSize == 0L && diskType.isEmpty && descr.isEmpty) {
        seq
      } else {
        val size = ByteStorageUnit(diskSize)
        val disk = if (isFlash) {
          FlashDisk(size, descr, "", "")
        } else {
          Disk(size, diskType, descr, "", "")
        }
        disk +: seq
      }
    }
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
