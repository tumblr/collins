package collins.util.plugins

import collins.models.User
import collins.models.Asset
import collins.models.AssetLifecycle
import collins.models.State
import collins.models.Status

object Maintenance {
  def toMaintenance(asset: Asset, reason: String, state: State, user: Option[User]): Boolean = {
    if (canTransitionToMaintenance(asset)) {
      AssetLifecycle.updateAssetStatus(asset, Status.Maintenance, Some(state), reason, user) match {
        case Left(e) => false
        case _ => true
      }
    } else {
      false
    }
  }

  def fromMaintenance(asset: Asset, reason: String, status: String, state: State, user: Option[User]): Boolean = {
    if (canTransitionFromMaintenance(asset)) {
      AssetLifecycle.updateAssetStatus(asset, Status.findByName(status), Some(state), reason, user) match {
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
