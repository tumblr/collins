package collins
package script

import models.Asset


/**
 * Contains helpers for decorating and listing asset search results.
 */
object SearchResults extends CollinScript {

  /**
   * Returns the CSS class for an Asset's row to signify the row's Bootstrap
   * coloration.
   *
   * @param asset an Asset whose row's coloration should be determined.
   * @return a String representing the Bootstrap CSS class by which to shade
   * an asset's row.
   */
  def getRowClassForAsset(asset: Asset): String = {
    asset match {
      case warn if warn.isIncomplete || warn.isNew => "warning"
      case info if info.isProvisioning || info.isProvisioned => "info"
      case ok if ok.isCancelled || ok.isDecommissioned => "success"
      case err if err.isMaintenance => "error"
      case default => ""
    }
  }

}
