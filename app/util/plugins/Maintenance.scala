package util
package plugins

import models.{Asset, AssetLifecycle, Status}

object Maintenance {
  def toMaintenance(asset: Asset, reason: String): Boolean = {
    if (canTransitionToMaintenance(asset)) {
      AssetLifecycle.updateAssetStatus(asset, Map("reason" -> reason, "status" -> "Maintenance")) match {
        case Left(e) => false
        case _ => true
      }
    } else {
      false
    }
  }

  def fromMaintenance(asset: Asset, reason: String, status: String): Boolean = {
    if (canTransitionFromMaintenance(asset)) {
      AssetLifecycle.updateAssetStatus(asset, Map("status" -> status, "reason" -> reason)) match {
        case Left(e) => false
        case _ => true
      }
    } else {
      false
    }
  }

  def canTransitionFromMaintenance(asset: Asset): Boolean = {
    import Status.Enum

    try {
      Status.Enum(asset.status) match {
        case Enum.Maintenance => true
        case _ => false
      }
    } catch {
      case e => false
    }
  }

  def canTransitionToMaintenance(asset: Asset): Boolean = {
    import Status.Enum

    try {
      Status.Enum(asset.status) match {
        case Enum.Decommissioned | Enum.Maintenance => false
        case _ => true
      }
    } catch {
      case e => false
    }
  }
}
