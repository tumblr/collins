package com.tumblr.indefatigable

import org.collectd.api._
import com.tumblr.stats.TsdbStats
import com.twitter.ostrich.stats.Stats
import com.twitter.ostrich.admin.{ServiceTracker, RuntimeEnvironment, Service}
import com.twitter.util.Eval
import java.io.File
import scala.None
import com.tumblr.ostrich.admin.TumblrRuntimeEnvironment


class CollectdInterface
  extends CollectdWriteInterface
     with CollectdInitInterface
     with CollectdConfigInterface
{
  val serviceName = "Indefatigable"

  var config: Option[IndefatigableConfig] = None
  var tsdbService: Option[Service] = None

  // register this consumer with collectd
  Collectd.registerInit(serviceName, this)
  Collectd.registerWrite(serviceName, this)
  Collectd.registerConfig(serviceName, this)
  
  def init() = try {
    if(config.isEmpty)
      throw new ExceptionInInitializerError("No configuration specified")

    // primary tsdb logging service
    tsdbService = Option(config.get.statsLogger()(Stats))
    tsdbService map { serv =>
      serv.start()
      ServiceTracker.register(serv)
    }

    config.get.thriftReceiverServiceConfig map { thriftConfig =>
      Collectd.logWarning("Starting thrift receiver")
      val runtime = TumblrRuntimeEnvironment(this, Array[String]())
      val thriftservice = thriftConfig(runtime)
      thriftservice.start()
    }
    
    Collectd.logWarning("Starting tick aggregator")
    val aggregator = new AggregatedStatsCollector(config.get)
    aggregator.start()
    ServiceTracker.register(aggregator)

    0 // success
  } catch {
    case e =>
    Collectd.logError("Exception starting consumer: "+e.toString)
    exit(1)
  }
  
  def config(configItem: OConfigItem) = {
    try {
      import scala.collection.JavaConversions._
      for(item <- configItem.getChildren) {
        val key = item.getKey
        key.toLowerCase match {
          case "config" if item.getValues.size == 1 =>
            val value = item.getValues.get(0)
            val eval = new Eval()
            config = Option(eval[IndefatigableConfig](new File(value.getString)))

          case _ =>
            throw new ExceptionInInitializerError("Unknown configuration option: "+key)
        }
      }

      if(config.isEmpty)
        config = Option(DefaultIndefatigableConfig)

      0 // success
    } catch {
      case e =>
      Collectd.logError("Exception configuring Indefatigable: "+e.toString)
      exit(1)
    }
  }

  def write(valList: ValueList) = {
    val sources = valList.getDataSet.getDataSources
    val values  = valList.getValues

    val plugin         = Option(valList.getPlugin)
    val pluginInstance = Option(valList.getPluginInstance)
    val valType        = Option(valList.getType)
    val typeInstance   = Option(valList.getTypeInstance)
    val host           = valList.getHost
    val timestamp      = valList.getTime / 1000
    
    // filter each value and slam it down the OpenTSDB pipe
    var i = 0
    while(i < sources.size()) {
      val pointName = sources.get(i).getName
      val value = values.get(i)

      val stat = CollectdStat(plugin, pluginInstance, valType, typeInstance, host, timestamp, pointName, value)
      val tsdbStat = config.get.statFilter(stat)

      TsdbStats(tsdbStat)

      i += 1
    }

    0 // success
  }
}
