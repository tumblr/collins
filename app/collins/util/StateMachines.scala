package collins.util

import collins.util.config.Feature

import collins.models.Asset
import collins.models.AssetMeta
import collins.models.AssetMetaValue
import collins.models.IpAddresses
import collins.models.IpmiInfo
import collins.models.State
import collins.models.Status
import collins.models.conversions.dateToTimestamp

import java.util.Date

case class AssetStateMachine(asset: Asset) {

  def canDecommission(): Boolean =
    asset.isCancelled || asset.isDecommissioned || asset.isMaintenance || asset.isUnallocated

  def decommission(): Option[Asset] = if (canDecommission) {
    val sid = State.Terminated.map(_.id).getOrElse(0)
    // FIXME change to partial update
    val newAsset = asset.copy(statusId = Status.Decommissioned.get.id, deleted = Some(new Date().asTimestamp), stateId = sid)
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
    } else {
      if (Feature.useWhiteListOnRepurpose) {
        if (Feature.keepSomeMetaOnRepurpose.size > 0) {
          val keepAttributes: Set[Long] = Feature.keepSomeMetaOnRepurpose.map(_.id)
          val allAttributes: Set[Long] = AssetMetaValue.findByAsset(asset).map(_.getMetaId()).toSet
          val deleteAttributes = allAttributes -- keepAttributes
          if (deleteAttributes.size > 0) {
            AssetMetaValue.deleteByAssetAndMetaId(asset, deleteAttributes)
          }
        }
      } else {
        if (Feature.deleteSomeMetaOnRepurpose.size > 0) {
          val deleteAttributes: Set[Long] = Feature.deleteSomeMetaOnRepurpose.map(_.id)
          AssetMetaValue.deleteByAssetAndMetaId(asset, deleteAttributes)
        }
      }
    }
    res
  } else {
    throw new Exception("Asset must be Unallocated, Cancelled or Decommissioned")
  }
}
