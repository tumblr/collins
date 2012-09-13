package collins
package script

import models.Asset
import models.Page
import util.plugins.SoftLayer
import play.api.Logger


object ShowIf extends CollinScript {

  def showHostname(assets: Page[Asset]): Boolean = {
    assets.items.find(_.getHostnameMetaValue.isDefined).map(_ => true)
      .getOrElse(false)
  }

  def showSoftLayerLink(assets: Page[Asset]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.collectFirst{
        case asset: Asset if(plugin.isSoftLayerAsset(asset)) => true
      }.getOrElse(false)
    }.getOrElse(false)
  }

}
