package models

import util._
import java.sql.Connection

object LshwHelper {
  import AssetMeta.Enum._

  type FilteredSeq[T] = Tuple2[Seq[T], Map[Int, Seq[MetaWrapper]]]
  type Reconstruction = Tuple2[LshwRepresentation,Seq[MetaWrapper]]

  def updateAsset(asset: Asset, lshw: LshwRepresentation)(implicit con: Connection): Boolean = {
    // FIXME: Need to delete specific asset meta values before accepting an update
    val mvs = construct(asset, lshw)
    mvs.size == AssetMetaValue.create(mvs)
  }
  def construct(asset: Asset, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    collectCpus(asset.getId, lshw) ++
      collectMemory(asset.getId, lshw) ++
      collectNics(asset.getId, lshw) ++
      collectDisks(asset.getId, lshw)
  }

  def reconstruct(asset: Asset, assetMeta: Seq[MetaWrapper]): Reconstruction = {
    val metaMap = assetMeta.groupBy { _.getGroupId }
    val (cpus,postCpuMap) = reconstructCpu(metaMap)
    val (memory,postMemoryMap) = reconstructMemory(postCpuMap)
    val (nics,postNicMap) = reconstructNics(postMemoryMap)
    val (disks,postDiskMap) = reconstructDisks(postNicMap)
    (LshwRepresentation(cpus, memory, nics, disks), postDiskMap.values.flatten.toSeq)
  }
  def reconstruct(asset: Asset): Reconstruction = {
    val assetMeta = AssetMetaValue.findAllByAssetId(asset.getId)
    reconstruct(asset, assetMeta)
  }

  protected def reconstructCpu(meta: Map[Int, Seq[MetaWrapper]]): FilteredSeq[Cpu] = {
    val cpuSeq = meta.get(0).map { seq =>
      val cpuCount = finder(seq, CpuCount, _.toInt, 0)
      val cpuCores = finder(seq, CpuCores, _.toInt, 0)
      val cpuThreads = finder(seq, CpuThreads, _.toInt, 0)
      val cpuSpeed = finder(seq, CpuSpeedGhz, _.toDouble, 0.0)
      val cpuDescription = finder(seq, CpuDescription, _.toString, "")
      (0 until cpuCount).map { id =>
        Cpu(cpuCores, cpuThreads, cpuSpeed, cpuDescription, "", "")
      }.toSeq
    }.getOrElse(Nil)
    val filteredMeta = meta.map { case(groupId, metaSeq) =>
      val newSeq = filterNot(
        metaSeq,
        Set(CpuCount.id, CpuCores.id, CpuThreads.id, CpuSpeedGhz.id, CpuDescription.id)
      )
      groupId -> newSeq
    }
    (cpuSeq, filteredMeta)
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

  private def filterNot(m: Seq[MetaWrapper], s: Set[Long]): Seq[MetaWrapper] = {
    m.filterNot { mw => s.contains(mw.getMetaId) }
  }
  private def finder[T](m: Seq[MetaWrapper], e: AssetMeta.Enum, c: (String => T), d: T): T = {
    m.find { _.getMetaId == e.id }.map { i => c(i.getValue) }.getOrElse(d)
  }

  protected def reconstructMemory(meta: Map[Int, Seq[MetaWrapper]]): FilteredSeq[Memory] = {
    val memSeq = meta.get(0).map { seq =>
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
    val filteredMeta = meta.map { case(groupId, metaSeq) =>
      val newSeq = filterNot(
        metaSeq,
        Set(MemoryAvailableBytes.id, MemoryBanksUsed.id, MemoryBanksUnused.id, MemoryDescription.id)
      )
      groupId -> newSeq
    }
    (memSeq, filteredMeta)
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

  protected def reconstructNics(meta: Map[Int, Seq[MetaWrapper]]): FilteredSeq[Nic] = {
    val nicSeq = meta.foldLeft(Seq[Nic]()) { case (seq, map) =>
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
    val filteredMeta = meta.map { case(groupId, metaSeq) =>
      val newSeq = filterNot(
        metaSeq,
        Set(NicSpeed.id, MacAddress.id, NicDescription.id)
      )
      groupId -> newSeq
    }
    (nicSeq, filteredMeta)
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

  protected def reconstructDisks(meta: Map[Int, Seq[MetaWrapper]]): FilteredSeq[Disk] = {
    val diskSeq = meta.foldLeft(Seq[Disk]()) { case (seq, map) =>
      val groupId = map._1
      val wrapSeq = map._2
      val diskSize = finder(wrapSeq, DiskSizeBytes, _.toLong, 0L)
      val diskType = finder(wrapSeq, DiskType, {s => Some(Disk.Type.withName(s))}, None)
      val descr = finder(wrapSeq, DiskDescription, _.toString, "")
      if (diskSize == 0L && diskType.isEmpty && descr.isEmpty) {
        seq
      } else {
        val size = ByteStorageUnit(diskSize)
        Disk(size, diskType.get, descr, "", "") +: seq
      }
    }
    val filteredMeta = meta.map { case(groupId, metaSeq) =>
      val newSeq = filterNot(
        metaSeq,
        Set(DiskSizeBytes.id, DiskType.id, DiskDescription.id)
      )
      groupId -> newSeq
    }
    (diskSeq, filteredMeta)
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
        AssetMetaValue(asset_id, DiskType.id, groupId, disk.diskType.toString),
        AssetMetaValue(asset_id, DiskDescription.id, groupId, "%s %s".format(disk.vendor, disk.product))
      ))
    }._2
    val diskSummary = AssetMetaValue(asset_id, DiskStorageTotal.id, lshw.totalUsableStorage.inBytes.toString)
    Seq(diskSummary) ++ physicalDisks
  }

}
