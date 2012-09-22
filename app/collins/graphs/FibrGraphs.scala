package collins.graphs

import models.asset.AssetView

import play.api.Application
import play.api.mvc.Content
import play.api.templates.Html

case class FibrGraphs(override val app: Application) extends GraphView {

  override def get(asset: AssetView): Option[Content] = {
    if (asset.isServerNode && asset.getHostnameMetaValue.isDefined) {
      Some(getIframe(asset.getHostnameMetaValue.get))
    } else {
      None
    }
  }

  override def validateConfig() {
    FibrGraphConfig.pluginInitialize(app.configuration)
  }

  protected def getIframe(hostname: String) = {
    collins.graphs.templates.html.fibr(hostname)
  }

}
