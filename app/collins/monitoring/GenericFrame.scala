package collins.monitoring

import play.api.Application
import play.api.mvc.Content

import collins.models.asset.AssetView

case class GenericFrame(override val app: Application) extends MonitoringView {

  override def isMonitorable(asset: AssetView): Boolean = {
    asset.isServerNode && asset.getHostnameMetaValue.isDefined
  }

  override def getContent(asset: AssetView): Option[Content] = {
    if (isMonitorable(asset)) {
      getIframe(asset)
    } else {
      None
    }
  }

  override def validateConfig() =
    GenericFrameConfig.pluginInitialize(app.configuration)

  protected def getIframe(asset: AssetView) =
    formatUrl(asset).map(a => collins.monitoring.templates.html.generic(a))

  protected def formatUrl(asset: AssetView) =
    asset.getHostnameMetaValue.map(h => GenericFrameConfig.urlTemplate.format(h))

}
