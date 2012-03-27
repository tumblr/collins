package com.tumblr.indefatigable

import com.tumblr.stats.Stat
import java.util.HashMap
import com.twitter.ostrich.stats.Stats
import org.collectd.api.Collectd

case class Filter() {
  var filterMap = Map[String, (CollectdStat => Stat[Double])]()

  var defaultFilter: (CollectdStat => Stat[Double]) = {
    case CollectdStat(pl, pli, ty, tyi, host, timestamp, name, value) =>
      def ap(sb: StringBuilder, str: Option[String]) {
        if(str.isEmpty || str.get.size < 1)
          return
        
        if(sb.size > 0)
          sb ++= "."
        sb ++= str.get
      }

      var key = new StringBuilder("")
      var tags = Map[String, String]()

      // build the key
      if(pl.isDefined)
        ap(key, pl)
      if(pl != ty && ty.isDefined && ty.get != "value")
        ap(key, ty)

      // build the tags
      if(pli.isDefined && pli.get != "value" && pli.get.size > 0)
        tags += (pl.get+"_id") -> pli.get
      if(pli != tyi && tyi.isDefined && tyi.get != "value" && tyi.get.size > 0)
        tags += (ty.get+"_id") -> tyi.get

      if(name != null && name != "value")
        tags += "type" -> name

      // generate stat
      Stat(timestamp, key.toString, value.doubleValue, host, tags)
  }

  def plugin(name:String)(fn: CollectdStat => Stat[Double]) = {
    filterMap += name -> fn
    this
  }
  
  def default(fn: CollectdStat => Stat[Double]) = {
    defaultFilter = fn
    this
  }

  def apply(stat: CollectdStat): Stat[Double] = {
    try {
      // ugly bit of kit, but it's tight
      if(stat.plugin.isDefined) {
        val filter = filterMap.get(stat.plugin.get)

        if(filter.isDefined) {
          val filtered = filter.get(stat)

          if(filtered != null)
            return filtered
        }
      }
      
      return defaultFilter(stat)
    } catch {
      case error =>
        defaultFilter(stat)
    }
  }
  
  def key(components: Option[String]*) = {
    var sb = new StringBuilder
    components.foreach { c =>
      if(c.isDefined) {
        if(sb.size > 0)
          sb ++= "."

        sb ++= c.get.toString
      }
    }

    sb.toString
  }
}


object DefaultCollectdFilter extends Filter {
  plugin("cpu") {
    case CollectdStat(_, no, _, tag, host, time, "value", value) =>
      Stat(time, "cpu", value.doubleValue, host, Map("cpu_id" -> no.get, "cpu_state" -> tag.get))
  }
  
  plugin("cpufreq") {
    case CollectdStat(_, _, _, no, host, time, "value", value) =>
      Stat(time, "cpu_freq", value.doubleValue, host, Map("cpu_id" -> no.get))
  }
  
  plugin("load") {
    case CollectdStat(_, _, _, _, host, time, term, value) =>
      term match {
        case "shortterm" => Stat(time, "load_short", value.doubleValue(), host, Map())
        case "midterm" =>   Stat(time, "load_mid",   value.doubleValue(), host, Map())
        case "longterm" =>  Stat(time, "load_long",  value.doubleValue(), host, Map())
      }
  }
  
  plugin("interface") {
    case CollectdStat(_, iface, metric, _, host, time, direction, value) =>
      Stat(time, metric.get, value.doubleValue, host, Map("iface" -> iface.get, "if_dir" -> direction))
  }
  
  plugin("swap") {
    case CollectdStat(_, _, _, state, host, time, _, value) =>
      Stat(time, "swap", value.doubleValue(), host, Map("swap_state" -> state.get))
  }
  
  plugin("memory") {
    case CollectdStat(_, _, _, state, host, time, _, value) =>
      Stat(time, "mem", value.doubleValue(), host, Map("mem_state" -> state.get))
  }
  
  plugin("disk") {
    case CollectdStat(_, dev, metric, _, host, time, kind, value) =>
      Stat(time, metric.get, value.doubleValue, host, Map("disk_dev" -> dev.get,  "disk_kind" -> kind))
  }
}