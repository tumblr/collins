package models

import util._

object LldpHelper extends CommonHelper[LldpRepresentation] {
  import AssetMeta.Enum._

  val managedTags = Set(
    LldpInterfaceName,
    LldpChassisName,
    LldpChassisIdType,
    LldpChassisIdValue,
    LldpChassisDescription,
    LldpPortIdType,
    LldpPortIdValue,
    LldpPortDescription,
    LldpVlanId,
    LldpVlanName
  )

  def construct(asset: Asset, lldp: LldpRepresentation): Seq[AssetMetaValue] = {
    if (lldp.interfaceCount < 1) {
      return Seq()
    }
    val asset_id = asset.getId
    lldp.interfaces.zipWithIndex.foldLeft(Seq[AssetMetaValue]()) { case(seq,tuple) =>
      val interface = tuple._1
      val groupId = tuple._2
      val chassis = interface.chassis
      val port = interface.port
      val vlans = interface.vlans.map { vlan =>
        Seq(AssetMetaValue(asset_id, LldpVlanId.id, groupId, vlan.id.toString),
            AssetMetaValue(asset_id, LldpVlanName.id, groupId, vlan.name))
      }.flatten
      seq ++ vlans ++ Seq(
        AssetMetaValue(asset_id, LldpInterfaceName.id, groupId, interface.name),
        AssetMetaValue(asset_id, LldpChassisName.id, groupId, chassis.name),
        AssetMetaValue(asset_id, LldpChassisIdType.id, groupId, chassis.id.idType),
        AssetMetaValue(asset_id, LldpChassisIdValue.id, groupId, chassis.id.value),
        AssetMetaValue(asset_id, LldpChassisDescription.id, groupId, chassis.description),
        AssetMetaValue(asset_id, LldpPortIdType.id, groupId, port.id.idType),
        AssetMetaValue(asset_id, LldpPortIdValue.id, groupId, port.id.value),
        AssetMetaValue(asset_id, LldpPortDescription.id, groupId, port.description)
      )
    }
  }

  def reconstruct(asset: Asset, assetMeta: Seq[MetaWrapper]): Reconstruction = {
    val metaMap = assetMeta.groupBy { _.getGroupId }
    val (interfaces,postMap) = reconstructInterfaces(metaMap)
    (LldpRepresentation(interfaces), postMap.values.flatten.toSeq)
  }

  protected def reconstructInterfaces(meta: Map[Int, Seq[MetaWrapper]]): FilteredSeq[Interface] = {
    val interfaceSeq = meta.foldLeft(Seq[Interface]()) { case(seq, map) =>
      val groupId = map._1
      val wrapSeq = map._2
      val interfaceName = finder(wrapSeq, LldpInterfaceName, _.toString, "")
      val chassisName = finder(wrapSeq, LldpChassisName, _.toString, "")
      val chassisIdType = finder(wrapSeq, LldpChassisIdType, _.toString, "")
      val chassisIdValue = finder(wrapSeq, LldpChassisIdValue, _.toString, "")
      val chassisDescr = finder(wrapSeq, LldpChassisDescription, _.toString, "")

      val portIdType = finder(wrapSeq, LldpPortIdType, _.toString, "")
      val portIdValue = finder(wrapSeq, LldpPortIdValue, _.toString, "")
      val portDescr = finder(wrapSeq, LldpPortDescription, _.toString, "")

      // FIXME We lose associations between the vlan name and the vlan ID because this abstraction
      // is: Interfaces -> Interface -> Vlans -> Vlan. An Interface has an associated groupId, but
      // then has multiple vlans associated with it. A groupId only provides one dimension per unit,
      // but the LLDP abstraction requires two dimensions. This only happens if an interface has
      // more than one VLAN
      val vlanId = seqFinder(wrapSeq, LldpVlanId, _.toInt)
      val vlanName = seqFinder(wrapSeq, LldpVlanName, _.toString)

      val isInvalid = Seq(interfaceName, chassisName, chassisIdType, chassisIdValue, chassisDescr,
        portIdType, portIdValue, portDescr).find { _.isEmpty }.isDefined
      if (isInvalid) {
        seq
      } else {
        val chassis = Chassis(chassisName, ChassisId(chassisIdType, chassisIdValue), chassisDescr)
        val port = Port(PortId(portIdType, portIdValue), portDescr)
        val vlans = vlanId.zip(vlanName).map(v => Vlan(v._1, v._2))
        Interface(interfaceName, chassis, port, vlans) +: seq
      }
    }
    val filteredMeta = meta.map { case(groupId, metaSeq) =>
      val newSeq = filterNot(
        metaSeq,
        Set(LldpInterfaceName.id, LldpChassisName.id, LldpChassisIdType.id, LldpChassisIdValue.id,
          LldpChassisDescription.id, LldpPortIdType.id, LldpPortIdValue.id, LldpPortDescription.id,
          LldpVlanId.id, LldpVlanName.id)
      )
      groupId -> newSeq
    }
    (interfaceSeq, filteredMeta)
  }
}
