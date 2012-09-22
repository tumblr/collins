package collins.graphs

import models.asset.AssetView
import util.config.TypesafeConfiguration
import play.api.mvc.Content

trait GraphView {
  protected val source: TypesafeConfiguration

  def get(asset: AssetView): Option[Content]
  def validateConfig() {
  }
}
