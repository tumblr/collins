package collins.util.plugins

import collins.models.User
import collins.models.Asset
import collins.models.AssetLifecycle
import collins.models.State
import collins.models.Status

object Maintenance {
  def toMaintenance(asset: Asset, reason: String, state: State, lifeCycle: AssetLifecycle): Boolean = {
    if (canTransitionToMaintenance(asset)) {
      lifeCycle.updateAssetStatus(asset, Status.Maintenance, Some(state), reason) match {
        case Left(e) => false
        case _ => true
      }
    } else {
      false
    }
  }

  def fromMaintenance(asset: Asset, reason: String, status: String, state: State, lifeCycle: AssetLifecycle): Boolean = {
    if (canTransitionFromMaintenance(asset)) {
      lifeCycle.updateAssetStatus(asset, Status.findByName(status), Some(state), reason) match {
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
