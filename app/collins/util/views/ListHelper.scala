package collins.util.views

import collins.models.Asset
import collins.models.asset.AssetView
import collins.models.shared.Page
import collins.softlayer.SoftLayer
import collins.softlayer.SoftLayerConfig
import collins.util.power.PowerComponent
import collins.util.power.PowerUnits
import collins.util.power.PowerUnits

// Mostly used with views/asset/list, also for comprehensions
object ListHelper {
  def showHostname(assets: Page[AssetView]): Boolean = {
    assets.items.find(_.getHostnameMetaValue.isDefined).map(_ => true).getOrElse(false)
  }
  def showSoftLayerLink(assets: Page[AssetView]): Boolean = {
    if (SoftLayerConfig.enabled) {
      assets.items.collectFirst{ case asset: Asset if(SoftLayer.isSoftLayerAsset(asset)) => true }.getOrElse(false)
    } else {
      false
    }
  }
  def getPowerComponentsInOrder(units: PowerUnits): Seq[PowerComponent] = {
    val components = units.flatMap { unit =>
      unit.components
    }
    components.toSeq.sorted
  }
  def getPowerComponentsInOrder(): Seq[PowerComponent] = {
    getPowerComponentsInOrder(PowerUnits())
  }
}
