package com.tumblr.indefatigable

import com.tumblr.thrift.ServiceInfo
import collection.Seq
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Future
import com.tumblr.stats.{TsdbStats, Stat}
import org.collectd.api.Collectd

class IndefatigableServiceImpl(config: IndefatigableServiceConfig)
  extends IndefatigableService
     with ServiceInfo
{
  val serverName = "Indefatigable"
  val runtime    = config.runtime
  val stats      = Stats.get("IndefatigableService")

  def sendStats(ticks: Seq[Tick]) = {
    for(tick <- ticks) {
      tick match {
        case Tick(serv, None, op, None) if op.isDefined =>
          CollectdRateStats.addMetric(serv+"_"+op.get, 0)

        case Tick(serv, time, None, tags) if tags.isDefined =>
          TsdbStats(Stat(serv, time.get, tags.get.toMap))

        case Tick(serv, time, op, None) if op.isDefined =>
          CollectdAggregatedStats.addMetric(serv+"_"+op.get, (time.get * 1000).ceil.toInt)

        case _ =>
          Collectd.logWarning("ignoring tick, invalid content: "+tick)
      }
    }

    Future.void
  }
}

class IndefatigableServerImpl(
    config: IndefatigableServiceConfig,
    val service: IndefatigableService
)
  extends IndefatigableServiceServer
{
  val serverName = "IndefatigableServer"
  var thriftServer: Option[IndefatigableServiceServer] = None
  
  def start() {
    log.info("Starting "+serverName)
    thriftServer.isDefined match {
      case true => throw new IllegalStateException("Server start already called")
      case false =>
        val _thriftServer = new IndefatigableThriftServerImpl(config, service)
        _thriftServer.start()
        thriftServer = Some(_thriftServer)
    }
  }

  def shutdown() {
    log.info("Shutting down "+serverName)
    thriftServer.map { _.shutdown() }
  }
}

class IndefatigableThriftServerImpl(
    config: IndefatigableServiceConfig,
    val service: IndefatigableService
)
  extends IndefatigableServiceThriftServer
{
  val thriftPort = config.thriftPort
  val serverName = "IndefatigableThriftServer"

  override def start() {
    println("-- start")
    log.info("Starting %s", serverName)
    Collectd.logWarning("indef thrift server started")
    super.start()
  }

  override def shutdown() {
    log.info("Shutting down %s", serverName)
    super.shutdown()
  }
}