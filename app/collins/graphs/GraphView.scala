package collins.graphs

import java.net.URLEncoder

import scala.util.control.Exception

import play.twirl.api.Content

import com.codahale.jerkson.Json.generate

import collins.models.asset.AssetView

sealed trait GraphView {
  def get(asset: AssetView): Option[Content]
  def isGraphable(asset: AssetView): Boolean = true
}

sealed class FibrGraphs extends GraphView {

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

  protected def getIframe(hostname: String) = {
    collins.graphs.templates.html.fibr(hostname)
  }
}

sealed class GangliaGraphs extends GraphView {

  override def get(asset: AssetView): Option[Content] = {
    if (isGraphable(asset)){
      Some(getIframe(generate_dynamic_view_json(asset.getHostnameMetaValue.get)))
    } else {
      None
    }
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

object GraphView extends GraphView {

  protected val underlying: Option[GraphView] = getGraphInstance()

  override def isGraphable(asset: AssetView): Boolean = {
    return underlying.map(_.isGraphable(asset)).getOrElse(false)
  }

  override def get(asset: AssetView): Option[Content] = {
    underlying.flatMap(_.get(asset))
  }

  private def getGraphInstance(): Option[GraphView] = {
    if (GraphConfig.enabled) {
      GraphConfig.className match {
        case "collins.graphs.FibrGraphs" => Some(new FibrGraphs())
        case "collins.graphs.GangliaGraphs" => Some(new GangliaGraphs())
      }
    } else {
      None
    }
  }

}