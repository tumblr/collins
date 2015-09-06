package collins.graphs

import com.google.common.cache.CacheBuilderSpec

import collins.guava.GuavaCacheFactory
import collins.util.config.Configurable

object GraphConfig extends Configurable {
  override val namespace = "graph"
  override val referenceConfigFilename = "graph_reference.conf"

  lazy protected[graphs] val metricCache =
    GuavaCacheFactory.create(cacheSpecificaton, MetricsCacheLoader())

  def enabled = getBoolean("enabled", false)
  def className = getString("class", "collins.graphs.FibrGraphs")
  def cacheSpecificaton = getString("cacheSpecification", "maximumSize=2000;expireAfterWrite=60s")

  override def validateConfig() {
    if (enabled) {
      className
      CacheBuilderSpec.parse(cacheSpecificaton)
    }
  }
}
