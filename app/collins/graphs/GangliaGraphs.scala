package collins.graphs

import models.asset.AssetView

import java.net.URLEncoder
import play.api.libs.json._
import com.codahale.jerkson.Json._

import play.api.Application
import play.api.mvc.Content
import play.api.templates.Html

case class GangliaGraphs(override val app: Application) extends GraphView {

  override def get(asset: AssetView): Option[Content] = {
    if (asset.isServerNode && asset.getHostnameMetaValue.isDefined) {
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
