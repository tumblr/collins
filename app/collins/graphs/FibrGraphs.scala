package collins.graphs

import play.api.Application
import play.api.mvc.Content
import play.api.templates.Html

import collins.models.asset.AssetView

case class FibrGraphs(override val app: Application) extends GraphView {

  override def get(asset: AssetView): Option[Content] = {
    if (isGraphable(asset)){
      Some(getIframe(asset.getHostnameMetaValue.get))
    } else {
      None
    }
  }

  override def isGraphable(asset: AssetView): Boolean = {
    asset.isServerNode && asset.getHostnameMetaValue.isDefined
  }

  override def validateConfig() {
    FibrGraphConfig.pluginInitialize(app.configuration)
  }

  protected def getIframe(hostname: String) = {
    collins.graphs.templates.html.fibr(hostname)
  }

}
