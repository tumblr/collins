package collins.util.views

import collins.models.Asset

object Titler {
  def apply(asset: Asset): String = {
    if (asset.getMetaAttribute("HOSTNAME").isDefined) {
      "Collins | %s - %s".format(asset.getMetaAttribute("HOSTNAME").get.getValue, asset.tag)
    } else {
      val assetType = asset.getType().label
      "Collins | %s - %s".format(asset.tag, assetType)
    }
  }

}
