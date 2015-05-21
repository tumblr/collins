package collins.util.views

import collins.models.Asset
import collins.models.asset.AssetView
import collins.models.shared.Page
import collins.util.plugins.SoftLayer
import collins.util.power.PowerComponent
import collins.util.power.PowerUnits
import collins.util.power.PowerUnits

// Mostly used with views/asset/list, also for comprehensions
object ListHelper {
  def showHostname(assets: Page[AssetView]): Boolean = {
    assets.items.find(_.getHostnameMetaValue.isDefined).map(_ => true).getOrElse(false)
  }
  def showSoftLayerLink(assets: Page[AssetView]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.collectFirst{ case asset: Asset if(plugin.isSoftLayerAsset(asset)) => true }.getOrElse(false)
    }.getOrElse(false)
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
