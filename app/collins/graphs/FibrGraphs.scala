package collins.graphs

import models.asset.AssetView

import util.config.{ConfigAccessor, ConfigSource, ConfigValue, TypesafeConfiguration}

import play.api.mvc.Content
import play.api.templates.Html

case class FibrGraphConfig(
  override val source: TypesafeConfiguration
) extends ConfigAccessor with ConfigSource {

  def url = getString("url")(ConfigValue.Required).get

  // 1 hour
  def timeframe = getMilliseconds("timeframe").getOrElse(3600000L)
  def timeframeSeconds = {
    timeframe / 1000L
  }

  def customMetrics = getObjectMap("customMetrics").map { case(k, v) =>
    k -> CustomMetricConfig(v.toConfig)
  }

  def customMetricsFor(hostname: String): Set[String] = {
    customMetrics.foldLeft(Set[String]()) { case(set, (key, metricConfig)) =>
      val selector = metricConfig.selector
      val selectorString =
        selector + " AND HOSTNAME=\"%s\"".format(hostname)
      set ++ GraphConfig.queryCache.get(MetricsQuery(selectorString, metricConfig.metrics))
    }
  }

  def defaultMetrics = getStringSet("defaultMetrics", Set(
    "SYS/LOAD", "SYS/MEM", "SYS/NET", "SYS/PROC" , "SYS/IO-UTIL"
  ))

  def annotations = getStringSet("annotations", Set("deploy"))

  def validateConfig() {
    url
    annotations
    customMetrics.foreach { case(k,v) =>
      v.validateConfig
    }
    defaultMetrics
    timeframe
  }
}

class FibrGraphs(override protected val source: TypesafeConfiguration) extends GraphView {
  protected val config = FibrGraphConfig(source)

  override def get(asset: AssetView): Option[Content] = {
    if (asset.isServerNode && asset.getHostnameMetaValue.isDefined) {
      Some(getIframe(asset.getHostnameMetaValue.get))
    } else {
      None
    }
  }

  override def validateConfig() {
    config.validateConfig()
  }

  protected def getIframe(hostname: String) = {
    collins.graphs.templates.html.fibr(config, hostname)
  }

}
