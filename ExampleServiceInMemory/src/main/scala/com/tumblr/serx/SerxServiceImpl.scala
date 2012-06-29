package com.tumblr.serx

import scala.collection.mutable
import com.tumblr.thrift.ServiceInfo
import com.twitter.finagle.tracing.Trace
import com.twitter.ostrich.stats.Stats
import com.twitter.thrift.generated.Status
import com.twitter.util.Future
import config.SerxServiceConfig
import java.nio.ByteBuffer

class SerxServiceImpl(config: SerxServiceConfig) extends SerxService.FutureIface with ServiceInfo {
  val serverName = "Serx"
  val runtime = config.runtime
  val stats = Stats.get("")

  // NOTE: This should be lifecycle managed
  status = Status.Alive

  /**
   * These services are based on finagle, which implements a nonblocking server.  If you
   * are making blocking rpc calls, it's really important that you run these actions in
   * a thread pool, so that you don't block the main event loop.  This thread pool is only
   * needed for these blocking actions.  The code looks like:
   *
   *     // Depends on com.twitter.util >= 1.6.10
   *     val futurePool = new FuturePool(Executors.newFixedThreadPool(config.threadPoolSize))
   *
   *     def hello() = futurePool {
   *       someService.blockingRpcCall
   *     }
   *
   */

  val database = new mutable.HashMap[String, String]()

  def get(key: String) = {
	Trace.recordRpcname("Serx", "get - enter")
    val ret = Stats.time("get") { // well return value from closure
      Future(database.get(key) match {
        case None =>
          log.debug("get %s: miss", key)
          Stats.incr("get.miss")
          throw new SerxException("No such key")
        case Some(value) =>
          log.debug("get %s: hit", key)
          Stats.incr("get.hit")
          value
      })
    } 
	Trace.recordRpcname("Serx", "get - exit")
	ret
  }

  def put(key: String, value: String) = {
    Trace.recordRpcname("Serx", "put - enter")
    log.debug("put %s", key)
    Stats.incr("put")
    database(key) = value
    Trace.recordRpcname("Serx", "put - exit")
    Future.Unit
  }
  
  def multiPut(key: String, value: String, key2: String, value2: String) = {
    Trace.recordRpcname("Serx", "multiPut - enter")
    put(key, value)
    put(key2, value2)
    Trace.recordRpcname("Serx", "multiPut - exit")
    Future.Unit
  }

  def clear() = {
    Trace.recordRpcname("Serx", "clear - enter")
    database.clear()
    Trace.recordRpcname("Serx", "clear - exit")
    Future.Unit
  }
}
