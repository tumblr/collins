package util

import models.{Asset, AssetMetaValue, IpmiInfo, Status}
import models.conversions._

import java.util.Date
import java.sql._

case class AssetStateMachine(asset: Asset) {
  import Status.Enum._

  def decommission()(implicit con: Connection): Option[Asset] = Status.Enum(asset.status) match {
    case Unallocated | Cancelled | Decommissioned =>
      val newAsset = asset.copy(status = Decommissioned.id, updated = Some(new Date().asTimestamp))
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
      res
  }

}

// Manages somewhat specific transitions for a given environment
class SoftLayerStateManager extends com.tumblr.play.state.Manager {
  type T = Asset

  import models.{AssetLifecycle, AssetType, MetaWrapper, Model}
  override def transition(old: Asset, current: Asset): Unit = {
    val types = Map("SERVER_NODE" -> Set("PRIMARY_ROLE","POOL"))
    AssetType.findById(current.asset_type)
      .map(at => AssetType.Enum(at.getId))
      .filter(at => types.contains(at.toString))
      .filter(_ => current.status == 2)
      .filter { at =>
        types(at.toString).foldLeft(0) { case(found, metaName) =>
          found + MetaWrapper.findMeta(current, metaName).map(_ => 1).getOrElse(0)
        } == types(at.toString).size
      }
      .foreach { t =>
        val options = Map(
          "status" -> "Allocated",
          "reason" -> "Triggered by rule"
        )
        AssetLifecycle.updateAssetStatus(current, options)
      }
  }
  override def canTransition(a: AnyRef): Boolean = {
    a.isInstanceOf[Asset]
  }
}
