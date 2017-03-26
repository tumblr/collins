package collins.models

import collins.models.AssetMeta.DynamicEnum._
import collins.models.AssetMeta.Enum._
import collins.models.lshw.Cpu
import collins.models.lshw.Disk
import collins.models.lshw.Memory
import collins.models.lshw.Nic
import collins.models.lshw.ServerBase
import collins.models.shared.CommonHelper
import collins.util.BitStorageUnit
import collins.util.ByteStorageUnit
import collins.util.LshwRepresentation

import scala.collection.immutable.SortedMap 

object LshwHelper extends CommonHelper[LshwRepresentation] {

  // TODO: Is this set actually used anywhere?
  val managedTags = Set(
    CpuCount,
    CpuCores,
    CpuThreads,
    CpuSpeedGhz,
    CpuDescription,
    MemorySizeBytes,
    MemoryDescription,
    MemorySizeTotal,
    MemoryBanksTotal,
    NicSpeed,
    MacAddress,
    NicDescription,
    DiskSizeBytes,
    DiskType,
    DiskDescription,
    DiskStorageTotal
  )

  def construct(asset: Asset, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    collectCpus(asset, lshw) ++
      collectMemory(asset, lshw) ++
      collectNics(asset, lshw) ++
      collectDisks(asset, lshw) ++
      collectBase(asset, lshw)
  }

  def reconstruct(asset: Asset, assetMeta: Seq[MetaWrapper]): Reconstruction = {
    val metaMap = SortedMap(assetMeta.groupBy { _.getGroupId }.toSeq.sortBy(_._1): _*)
    val (cpus,postCpuMap) = reconstructCpu(metaMap)
    val (memory,postMemoryMap) = reconstructMemory(postCpuMap)
    val (nics,postNicMap) = reconstructNics(postMemoryMap)
    val (disks,postDiskMap) = reconstructDisks(postNicMap)
    val (base,postBaseMap) = reconstructBase(postDiskMap)
    (LshwRepresentation(cpus, memory, nics, disks, base.headOption.getOrElse(ServerBase())), postBaseMap.values.flatten.toSeq)
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
  protected def collectCpus(asset: Asset, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.cpuCount < 1) {
      return Seq()
    }
    val cpu = lshw.cpus.find(cpu => cpu.description.nonEmpty).getOrElse(lshw.cpus.head)
    Seq(
      AssetMetaValue(asset, CpuCount.id, lshw.cpuCount.toString),
      AssetMetaValue(asset, CpuCores.id, cpu.cores.toString),
      AssetMetaValue(asset, CpuThreads.id, cpu.threads.toString),
      AssetMetaValue(asset, CpuSpeedGhz.id, cpu.speedGhz.toString),
      AssetMetaValue(asset, CpuDescription.id, "%s %s".format(cpu.product, cpu.vendor))
    )
  }

  protected def reconstructMemory(meta: Map[Int, Seq[MetaWrapper]]): FilteredSeq[Memory] = {
    if (!meta.contains(0)) {
      return (Seq[Memory](), meta)
    }
    val totalBanks = finder(meta(0), MemoryBanksTotal, _.toInt, 0)
    val memSeq = (0 until totalBanks).map { bankId =>
      meta.get(bankId).map { seq =>
        val memorySizeBytes = finder(seq, MemorySizeBytes, _.toLong, 0L)
        val description = finder(seq, MemoryDescription, _.toString, "")
        if (memorySizeBytes == 0L && description.isEmpty) {
          Memory(ByteStorageUnit(0L), bankId, "Empty Memory Bank", "", "")
        } else {
          Memory(ByteStorageUnit(memorySizeBytes), bankId, description, "", "")
        }
      }.getOrElse(Memory(ByteStorageUnit(0L), bankId, "Empty Memory Bank", "", ""))
    }.toSeq
    val filteredMeta = meta.map { case(groupId, metaSeq) =>
      val newSeq = filterNot(
        metaSeq,
        Set(MemorySizeBytes.id, MemoryDescription.id, MemorySizeTotal.id, MemoryBanksTotal.id)
      )
      groupId -> newSeq
    }
    (memSeq, filteredMeta)
  }
  protected def collectMemory(asset: Asset, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.memoryBanksTotal < 1) {
      return Seq()
    }
    lshw.memory.filter { _.size.bytes > 0 }.foldLeft(Seq[AssetMetaValue]()) { case(total, current) =>
      val memory = current
      val groupId = memory.bank
      total ++ Seq(
        AssetMetaValue(asset, MemorySizeBytes.id, groupId, memory.size.bytes.toString),
        AssetMetaValue(asset, MemoryDescription.id, groupId, "%s - %s %s".format(
          memory.description, memory.vendor, memory.product
        ))
      )
    } ++ Seq(
      AssetMetaValue(asset, MemorySizeTotal.id, lshw.totalMemory.bytes.toString),
      AssetMetaValue(asset, MemoryBanksTotal.id, lshw.memoryBanksTotal.toString)
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
  protected def collectNics(asset: Asset, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.nicCount < 1) {
      return Seq()
    }
    lshw.nics.foldLeft((0,Seq[AssetMetaValue]())) { case (run,nic) =>
      val groupId = run._1
      val total = run._2
      val res: Seq[AssetMetaValue] = Seq(
        AssetMetaValue(asset, NicSpeed.id, groupId, nic.speed.inBits.toString),
        AssetMetaValue(asset, MacAddress.id, groupId, nic.macAddress),
        AssetMetaValue(asset, NicDescription.id, groupId, "%s - %s".format(nic.product, nic.vendor))
      )
      (groupId + 1, total ++ res)
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
  protected def collectDisks(asset: Asset, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    if (lshw.diskCount < 1) {
      return Seq()
    }
    val physicalDisks = lshw.disks.foldLeft((0,Seq[AssetMetaValue]())) { case (run,disk) =>
      val groupId = run._1
      val total = run._2
      (groupId + 1, total ++ Seq(
        AssetMetaValue(asset, DiskSizeBytes.id, groupId, disk.size.inBytes.toString),
        AssetMetaValue(asset, DiskType.id, groupId, disk.diskType.toString),
        AssetMetaValue(asset, DiskDescription.id, groupId, "%s %s".format(disk.vendor, disk.product))
      ))
    }._2
    val diskSummary = AssetMetaValue(asset, DiskStorageTotal.id, lshw.totalUsableStorage.inBytes.toString)
    Seq(diskSummary) ++ physicalDisks
  }

  protected def reconstructBase(meta: Map[Int, Seq[MetaWrapper]]): FilteredSeq[ServerBase] = {
    val baseSeq = meta.get(0).map { seq =>
      val baseDescription = amfinder(seq, BaseDescription, _.toString, "")
      val baseProduct = amfinder(seq, BaseProduct, _.toString, "")
      val baseVendor = amfinder(seq, BaseVendor, _.toString, "")
      val baseSerial = amfinder(seq, BaseSerial, x => if (x.isEmpty) { None } else { Some(x) }, None)
      Seq(ServerBase(baseDescription, baseProduct, baseVendor, baseSerial))
    }.getOrElse(Nil)

    val filteredMeta = meta.map { case(groupId, metaSeq) =>
      val newSeq = filterNot(
        metaSeq,
        Set(BaseDescription.id, BaseProduct.id, BaseVendor.id, BaseSerial.id)
      )
      groupId -> newSeq
    }
    (baseSeq, filteredMeta)
  }

  protected def collectBase(asset: Asset, lshw: LshwRepresentation): Seq[AssetMetaValue] = {
    val base = lshw.base
    val expectedAttrs = Seq(
      AssetMetaValue(asset, BaseDescription.id, base.description),
      AssetMetaValue(asset, BaseProduct.id, base.product),
      AssetMetaValue(asset, BaseVendor.id, base.vendor)
    )
    base.serial match {
      case Some(x) => expectedAttrs ++ Seq(AssetMetaValue(asset, BaseSerial.id, base.serial.get))
      case None    => expectedAttrs
    }
  }

}
