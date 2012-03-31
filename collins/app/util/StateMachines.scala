package util

import models.{Asset, AssetMeta, AssetMetaValue, IpAddresses, IpmiInfo, Status}
import models.conversions._

import java.util.Date
import java.sql._

object AssetStateMachine {
  lazy val DeleteSomeAttributes: Set[String] = Helpers.getFeature("deleteSomeMetaOnRepurpose").map { v =>
    v.split(",").map(_.trim.toUpperCase).toSet
  }.getOrElse(Set[String]())
  lazy val DeleteAttributes: Set[Long] = DeleteSomeAttributes.map { v =>
    AssetMeta.findByName(v).map(_.getId).getOrElse(-1L)
  }
}

case class AssetStateMachine(asset: Asset) {
  import Status.Enum._

  def decommission(): Option[Asset] = Status.Enum(asset.status) match {
    case Unallocated | Cancelled | Decommissioned =>
      val newAsset = asset.copy(status = Decommissioned.id, deleted = Some(new Date().asTimestamp))
      val res = Asset.update(newAsset) match {
        case 1 => Some(newAsset)
        case n => None
      }
      Feature("deleteIpmiOnDecommission").whenEnabledOrUnset {
        IpmiInfo.deleteByAsset(asset)
      }
      Feature("deleteIpAddressOnDecommission").whenEnabledOrUnset {
        IpAddresses.deleteByAsset(asset)
      }
      Feature("deleteMetaOnDecommission").whenEnabledOrUnset {
        AssetMetaValue.deleteByAsset(asset)
      }.orElse {
        AssetMetaValue.deleteByAssetAndMetaId(asset, AssetStateMachine.DeleteAttributes)
      }
      res
  }
}
