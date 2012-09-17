package util

import config.Feature

import models.{Asset, AssetMeta, AssetMetaValue, IpAddresses, IpmiInfo, State, Status}
import models.conversions._

import java.util.Date
import java.sql._

case class AssetStateMachine(asset: Asset) {

  def canDecommission(): Boolean =
    asset.isCancelled || asset.isDecommissioned || asset.isMaintenance || asset.isUnallocated

  def decommission(): Option[Asset] = if (canDecommission) {
    val sid = State.Terminated.map(_.id).getOrElse(0)
    // FIXME change to partial update
    val newAsset = asset.copy(status = Status.Decommissioned.get.id, deleted = Some(new Date().asTimestamp), state = sid)
    val res = Asset.update(newAsset) match {
      case 1 => Some(newAsset)
      case n => None
    }
    if (Feature.deleteIpmiOnDecommission) {
      IpmiInfo.deleteByAsset(asset)
    }
    if (Feature.deleteIpAddressOnDecommission) {
      IpAddresses.deleteByAsset(asset)
    }
    if (Feature.deleteMetaOnDecommission) {
      AssetMetaValue.deleteByAsset(asset)
    } else if (Feature.deleteSomeMetaOnRepurpose.size > 0) {
      val deleteAttributes: Set[Long] = Feature.deleteSomeMetaOnRepurpose.map(_.id)
      AssetMetaValue.deleteByAssetAndMetaId(asset, deleteAttributes)
    }
    res
  } else {
    throw new Exception("Asset must be Unallocated, Cancelled or Decommissioned")
  }
}
