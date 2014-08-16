package collins.monitoring

import models.asset.AssetView

import play.api.Application
import play.api.mvc.Content
import play.api.templates.Html

case class Icinga2(override val app: Application) extends MonitoringView {

  override def isMonitorable(asset: AssetView): Boolean = {
    asset.isServerNode && asset.getHostnameMetaValue.isDefined
  }

  override def getContent(asset: AssetView): Option[Content] = {
    if (isMonitorable(asset)) {
      Some(getIframe(asset))
    } else {
      None
    }
  }

  override def validateConfig() {
    Icinga2Config.pluginInitialize(app.configuration)
  }

  protected def getIframe(asset: AssetView) = {
    collins.monitoring.templates.html.icinga2(asset.getHostnameMetaValue.get)
  }

}
