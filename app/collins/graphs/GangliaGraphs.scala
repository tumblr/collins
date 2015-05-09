package collins.graphs

import java.net.URLEncoder
import com.codahale.jerkson.Json.generate

import play.api.Application
import play.api.mvc.Content
import play.api.templates.Html

import collins.models.asset.AssetView

case class GangliaGraphs(override val app: Application) extends GraphView {

  override def get(asset: AssetView): Option[Content] = {
    if (isGraphable(asset)){
      Some(getIframe(generate_dynamic_view_json(asset.getHostnameMetaValue.get)))
    } else {
      None
    }
  }

  override def validateConfig() {
    GangliaGraphConfig.pluginInitialize(app.configuration)
  }

  protected def getIframe(view_json: String) = {
    collins.graphs.templates.html.ganglia(URLEncoder.encode(view_json, "UTF-8"))
  }

  override def isGraphable(asset: AssetView): Boolean = {
    asset.isServerNode && asset.getHostnameMetaValue.isDefined
  }

  def hostKey(hostname: String): String = {
    hostname + GangliaGraphConfig.hostSuffix
  }

  def generate_dynamic_view_json(hostname: String): String = {
    generate(
      Map(
        "view_name" -> "ad-hoc",
        "view_type" -> "standard",
        "items" -> (GangliaGraphConfig.defaultGraphs.map(g =>
          Map(
            "hostname" -> hostKey(hostname),
            "graph" -> g
          )) ++ GangliaGraphConfig.defaultMetrics.map(m =>
            Map(
              "hostname" -> hostKey(hostname),
              "metric" -> m
            )
        ))
      )
    )
  }

}
