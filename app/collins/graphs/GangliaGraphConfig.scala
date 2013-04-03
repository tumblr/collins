package collins.graphs

import util.config.{Configurable, ConfigValue}

// todo: support by role custom metrics/graphs
object GangliaGraphConfig extends Configurable {

  override val namespace = "graph.GangliaGraphs"
  override val referenceConfigFilename = "ganglia_graph_reference.conf"

  def url = getString("url")(ConfigValue.Required).get

  def timeRange = getString("timeRange", "1hr");

  def hostSuffix = getString("hostSuffix", "");

  // Ganglia has a distinction between graphs of individual metrics
  // "load_one", and some pre-created reports that pulls things
  // together in one graph image (show load_one, and load_five).
  // Confusingly, these are sometimes called graphs, and sometimes
  // reports.
  def defaultGraphs = getStringList("defaultGraphs", List(
    "load_all_report", "load_report", "mem_report", "cpu_report", "network_report", "packet_report"
  ))

  def defaultMetrics = getStringList("defaultMetrics", List(
    "disk_free", "disk_total"
  ))

  override def validateConfig() {
    url
    hostSuffix
    timeRange
    defaultGraphs
    defaultMetrics
  }

}
