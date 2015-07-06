package collins.util.views

import play.api.Play

import collins.models.Asset
import collins.models.asset.AssetView
import collins.provisioning.ProvisionerConfig
import collins.softlayer.SoftLayerConfig
import collins.softlayer.SoftLayer

object SoftLayerHelper {

  def ticketLink(id: String): Option[String] = {
    try {
      Some(SoftLayer.ticketUrl(id.toLong))
    } catch {
      case _: Throwable => None
    }
  }

  def assetLink(asset: AssetView): Option[String] = asset match {
    case a: Asset => {
      SoftLayer.softLayerUrl(a)
    }
    case _ => None
  }

  def canCancel(asset: Asset): Boolean = {
    validAsset(asset) && SoftLayerConfig.allowedCancelStatus.contains(asset.status)
  }

  def canActivate(asset: Asset): Boolean = {
    validAsset(asset) && asset.isIncomplete
  }

  protected def validAsset(asset: Asset): Boolean = {
    SoftLayer.isSoftLayerAsset(asset) &&
      ProvisionerConfig.allowedType(asset.asset_type)
  }

}
