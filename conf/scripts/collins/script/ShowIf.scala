package collins
package script

import models.Asset
import models.Page
import util.plugins.SoftLayer
import play.api.Logger


/**
 * A collection of methods to determine whether an attribute should be shown
 * for a collection of Assets intended for rendering to HTML.
 */
object ShowIf extends CollinScript {

  /**
   * Returns a Boolean specifying whether the Hostname attribute should be
   * shown for a collection of Assets.
   *
   * @param assets a Page of assets intended for rendering to HTML
   * @return a Boolean specifying whether the attribute should be shown.
   */
  def showHostname(assets: Page[Asset]): Boolean = {
    assets.items.find(_.getHostnameMetaValue.isDefined).map(_ => true)
      .getOrElse(false)
  }

  /**
   * Returns a Boolean specifying whether the SoftLayer Link attribute should
   * be shown for a collection of Assts.
   *
   * @param assets a Page of assets intended for rendering to HTML.
   * @return a Boolean specifying whether the attribute should be shown.
   */
  def showSoftLayerLink(assets: Page[Asset]): Boolean = {
    SoftLayer.pluginEnabled { plugin =>
      assets.items.collectFirst{
        case asset: Asset if(plugin.isSoftLayerAsset(asset)) => true
      }.getOrElse(false)
    }.getOrElse(false)
  }

}
