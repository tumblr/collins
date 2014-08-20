package collins.graphs

import models.asset.AssetView
import play.api.Application
import play.api.mvc.Content

trait GraphView {
  val app: Application
  def get(asset: AssetView): Option[Content]
  def isGraphable(asset: AssetView): Boolean = true
  def validateConfig() {
  }
}
