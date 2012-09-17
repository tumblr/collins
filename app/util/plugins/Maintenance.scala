package util
package plugins

import models.{Asset, AssetLifecycle, State, Status}

object Maintenance {
  def toMaintenance(asset: Asset, reason: String, state: State): Boolean = {
    if (canTransitionToMaintenance(asset)) {
      AssetLifecycle.updateAssetStatus(asset, Status.Maintenance, Some(state), reason) match {
        case Left(e) => false
        case _ => true
      }
    } else {
      false
    }
  }

  def fromMaintenance(asset: Asset, reason: String, status: String, state: State): Boolean = {
    if (canTransitionFromMaintenance(asset)) {
      AssetLifecycle.updateAssetStatus(asset, Status.findByName(status), Some(state), reason) match {
        case Left(e) => false
        case _ => true
      }
    } else {
      false
    }
  }

  def canTransitionFromMaintenance(asset: Asset): Boolean = asset.isMaintenance
  def canTransitionToMaintenance(asset: Asset): Boolean = {
    if (asset.isDecommissioned || asset.isMaintenance) {
      false
    } else {
      true
    }
  }
}
