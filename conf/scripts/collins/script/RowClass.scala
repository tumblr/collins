package collins
package script

import models.Asset


object RowClass extends CollinScript {
  
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