package collins.monitoring

import models.asset.AssetView
import play.api.Application
import play.api.mvc.Content

trait MonitoringView {
  val app: Application
  def getContent(asset: AssetView): Option[Content]
  def isMonitorable(asset: AssetView): Boolean
  def validateConfig() { }
}
