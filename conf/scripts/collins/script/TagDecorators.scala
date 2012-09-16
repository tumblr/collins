package collins
package script

import models.Asset
import models.asset.AssetView
import util.config.ActionConfig
import util.plugins.SoftLayer


/**
 * A collection of decorators with which to decorate attributes of a
 * Collins Asset suitable for formatting.
 */
object TagDecorators extends CollinScript {

  /**
   * Returns the Asset's date of creation as a JDBC-formatted timestamp String.
   *
   * @param asset an Asset whose date of creation will be returned.
   * @return the date of creation of an Asset.
   */
  def decorateCreated(assetString: String): String = {
    val created = asset.created
    if (created == None) {
      ""
    } else {
      created.toString
    }
  }

  /**
   * Returns the hostname of an Asset as a String.
   *
   * @param asset an Asset whose hostname will be returned.
   * @return a String containing the hostname of an Asset.
   */
  def decorateHostname(asset: Asset): String = {
    val hostname = asset.getHostnameMetaValue
    if (hostname == None) {
      ""
    } else {
      hostname.toString
    }
  }

  /**
   * Returns the SoftLayer link corresponding to an Asset as a String suitable
   * for rendering.
   *
   * @param asset an Asset whose SoftLayer link will be returned.
   * @return a String containing a SoftLayer link.
   */
  def decorateSLLink(asset: Asset): String = {
    return SoftLayer.assetLink(asset).map{ url => 
        "<a href=\"%s\" target=\"_blank\">SL</a>".format(url)
    } match {
      case Some(url) => url
      case None => ""
    }
  }

  /**
   * Returns the status of an Asset as a String.
   *
   * @param asset an Asset whose status will be returned.
   * @return a String containing the status of an Asset.
   */
  def decorateStatus(asset: Asset): String = {
    val status = asset.getStatusName
    if (status == None) {
      ""
    } else {
      status.toString
    }
  }

  /**
   * Returns a link corresponding to an Asset Tag of an Asset as a String
   * suitable for rendering.
   *
   * @param asset an Asset whose tag will be formatted as an URL.
   * @return a link formatted with the Asset's Asset Tag URL
   */
  def decorateTag(asset: Asset): String = {
    return "<a href=\"%s\">%s</a>".format(getAssetURL(asset), asset.tag)
  }

  /**
   * Returns an asset's date of last update as a JDBC-formatted timestamp
   * String.
   *
   * @param asset an Asset whose date of last update will be returned.
   * @return a String containing the date of last update.
   */
  def decorateUpdated(asset: Asset): String = {
    val updated = asset.updated
    if (updated == None) {
      ""
    } else {
      updated.toString
    }
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
