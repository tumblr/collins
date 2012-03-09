package util

import models.{Asset, AssetMeta, AssetMetaValue, IpmiInfo, Status}
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
  lazy val DeleteAttributesString = DeleteAttributes.mkString(",")
}

case class AssetStateMachine(asset: Asset) {
  import Status.Enum._

  def decommission()(implicit con: Connection): Option[Asset] = Status.Enum(asset.status) match {
    case Unallocated | Cancelled | Decommissioned =>
      val newAsset = asset.copy(status = Decommissioned.id, deleted = Some(new Date().asTimestamp))
      val res = Asset.update(newAsset) match {
        case 1 => Some(newAsset)
        case n => None
      }
      Helpers.haveFeature("deleteIpmiOnDecommission", false) match {
        case None | Some(true) =>
          IpmiInfo.delete("asset_id={id}").on('id -> asset.getId).executeUpdate()
        case _ =>
      }
      Helpers.haveFeature("deleteMetaOnDecommission", false) match {
        case None | Some(true) =>
          AssetMetaValue.delete("asset_id={id}").on('id -> asset.getId).executeUpdate()
        case _ =>
      }
      AssetMetaValue.delete("asset_id={id} AND asset_meta_id IN(%s)".format(AssetStateMachine.DeleteAttributesString))
        .on('id -> asset.getId)
        .executeUpdate()
      res
  }
}
