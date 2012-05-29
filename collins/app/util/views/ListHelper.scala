package util
package views

import models.{Asset, AssetView, Page}

// Used with views/asset/list
object ListHelper {
  def showHostname(assets: Page[AssetView]): Boolean = {
    assets.items.find(_.getHostnameMetaValue.isDefined).map(_ => true).getOrElse(false)
  }
  def showSoftLayerLink(assets: Page[AssetView]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.collectFirst{ case asset: Asset if(plugin.isSoftLayerAsset(asset)) => true }.getOrElse(false)
    }.getOrElse(false)
  }
}
