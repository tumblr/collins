package util

import config.Feature

import models.{Asset, AssetMeta, AssetMetaValue, IpAddresses, IpmiInfo, Status}
import models.conversions._

import java.util.Date
import java.sql._

case class AssetStateMachine(asset: Asset) {
  import Status.Enum._

  def canDecommission(): Boolean =
    asset.isCancelled || asset.isDecommissioned || asset.isMaintenance || asset.isUnallocated

  def decommission(): Option[Asset] = Status.Enum(asset.status) match {
    case Cancelled | Decommissioned | Maintenance | Unallocated =>
      val newAsset = asset.copy(status = Decommissioned.id, deleted = Some(new Date().asTimestamp))
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
    case _ =>
      throw new Exception("Asset must be Unallocated, Cancelled or Decommissioned")
  }
}
