package collins.util.views

import collins.models.Asset
import collins.models.asset.AssetView
import collins.provisioning.ProvisionerConfig
import collins.softlayer.SoftLayer
import collins.softlayer.SoftLayerConfig

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
    validAsset(asset) && SoftLayerConfig.allowedCancelStatus.contains(asset.statusId)
  }

  def canActivate(asset: Asset): Boolean = {
    validAsset(asset) && asset.isIncomplete
  }

  protected def validAsset(asset: Asset): Boolean = {
    SoftLayer.isSoftLayerAsset(asset) &&
      ProvisionerConfig.allowedType(asset.assetTypeId)
  }

}
