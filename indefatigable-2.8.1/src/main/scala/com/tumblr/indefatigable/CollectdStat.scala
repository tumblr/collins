package com.tumblr.indefatigable

import com.twitter.ostrich.admin.PeriodicBackgroundProcess
import com.tumblr.stats.{TsdbStats, Stat}
import org.collectd.api.Collectd
import com.twitter.ostrich.stats.{Distribution, StatsCollection}
import scala.collection.mutable

case class CollectdStat(
  plugin:     Option[String],
  pluginInstance: Option[String],
  valType:    Option[String],
  typeInstance:   Option[String],
  host:       String,
  timestamp:  Long,
  name:       String,
  value:      Number
)

/**
 * container for storing the aggregated tick stats coming
 * through the thrift interface
 */
object CollectdAggregatedStats extends StatsCollection {
  includeJvmStats = false
}

/**
 * container for storing rate metrics for tick stats coming
 * through the thrift interface
 */
object CollectdRateStats extends StatsCollection {
  includeJvmStats = false
}

class AggregatedStatsCollector (
  config: IndefatigableConfig
)
  extends PeriodicBackgroundProcess("AggregatedStatsCollector", config.statsLogger.reportingPeriod)
{
  def periodic() {
    try {
      var metrics: mutable.HashMap[String, Distribution] = null

      // block the stats object
      metrics = CollectdAggregatedStats.getMetrics()
      CollectdAggregatedStats.clearAll()

      metrics foreach { case ((metric, dist)) =>
        val components = metric.split("_", 2)
        if(components.size == 2) {
          val serv = components(0)
          val op   = components(1)
          val tags = Map("op" -> op)
          val hist = dist.histogram
          
          val stats = Seq(
            Stat(serv+"_hits", dist.count,   tags),
            Stat(serv+"_avg",  dist.average, tags),
            Stat(serv+"_max",  dist.maximum, tags),
  
            Stat(serv+"_50",   hist.getPercentile(.50),   tags),
            Stat(serv+"_75",   hist.getPercentile(.75),   tags),
            Stat(serv+"_99",   hist.getPercentile(.99),   tags),
            Stat(serv+"_999",  hist.getPercentile(.999),  tags),
            Stat(serv+"_9999", hist.getPercentile(.9999), tags)
          )
          TsdbStats(stats)
        } else {
          Collectd.logError("Metric has unknown format: "+metric)
        }
      }
      
      metrics = CollectdRateStats.getMetrics()
      CollectdRateStats.clearAll()
      
      metrics foreach { case ((metric,dist)) =>
        val components = metric.split("_", 2)
        if(components.size == 2) {
          val serv = components(0)
          val op   = components(1)
          val tags = Map("op" -> op)

          TsdbStats(Stat(serv, dist.count, tags))
        }
      }
    } catch {
      case e => 
        Collectd.logError("Exception from AggregatedStatsCollector: ")
        Collectd.logError(e.getMessage)
        for(line <- e.getStackTrace)
          Collectd.logError(line.getMethodName+" "+line.getFileName+":"+line.getLineNumber)
    }
  }
}