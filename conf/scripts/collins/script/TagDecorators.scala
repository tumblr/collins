package collins
package script

import models.Asset


object TagDecorators extends CollinScript {

  def decorateTag(asset: Asset): String = {
    return "<a href=\"%s\">%s</a>".format(getAssetURL(asset), asset.tag)
  }

  def decorateCreated(asset: Asset): String = {
    return asset.created.toString
  }

  def decorateHostname(asset: Asset): String = {
    return asset.getHostnameMetaValue.toString
  }

  def decorateStatus(asset: Asset): String = {
    return asset.getStatusName.toString
  }

  def decorateUpdated(asset: Asset): String = {
    return asset.updated.toString
  }

  def decorateSLLink(asset: Asset): String = {
    this.getClass.getClassLoader.loadClass("views.html.asset.slLink").getMethod(
        "render", classOf[Asset], classOf[String]).invoke(this, asset, "")
      .toString
  }

  /**
   * Returns the URL where an asset can be found.
   *
   * @param asset an AssetView representing an asset
   * @return a String containing an URL to the supplied Asset
   */
  private def getAssetURL(asset: Asset): String = {
    asset.remoteHost.getOrElse("") + app.routes.CookieApi.getAsset(asset.tag)
  }

}
