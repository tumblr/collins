package util
package views

import models.{Asset, Page}

// Used with views/asset/list
object ListHelper {
  def showHostname(assets: Page[Asset]): Boolean = {
    assets.items.find(_.getMetaAttribute("HOSTNAME").isDefined).map(_ => true).getOrElse(false)
  }
  def showSoftLayerLink(assets: Page[Asset]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.find(asset => plugin.isSoftLayerAsset(asset)).map(_ => true).getOrElse(false)
    }.getOrElse(false)
  }
}
