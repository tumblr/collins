package collins.monitoring

import play.api.Application
import play.api.mvc.Content

import collins.models.asset.AssetView

trait MonitoringView {
  val app: Application
  def getContent(asset: AssetView): Option[Content]
  def isMonitorable(asset: AssetView): Boolean
  def validateConfig() { }
}
