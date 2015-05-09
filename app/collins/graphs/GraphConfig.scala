package collins.graphs

import collins.cache.ConfigCache
import collins.util.config.Configurable

object GraphConfig extends Configurable {
  override val namespace = "graph"
  override val referenceConfigFilename = "graph_reference.conf"

  lazy protected[graphs] val queryCache =
    ConfigCache.create(GraphConfig.cacheTimeout, GraphConfig.cacheSize, MetricsCacheLoader())

  def enabled = getBoolean("enabled", false)
  def className = getString("class", "collins.graphs.FibrGraphs")
  def cacheSize = getInt("queryCacheSize", 2000)
  def cacheTimeout = getMilliseconds("queryCacheTimeout").getOrElse(60000L)

  override def validateConfig() {
    if (enabled) {
      className
      cacheSize
      cacheTimeout
    }
  }
}
