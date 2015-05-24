package collins.util

import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import scala.collection.JavaConverters.mapAsScalaConcurrentMapConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.immutable.SortedMap

import com.codahale.metrics.MetricRegistry

import nl.grons.metrics.scala.InstrumentedBuilder
import nl.grons.metrics.scala.Timer

sealed trait AppStats {
  val name: String
  def toMap: Map[String, String]
}

case class AppTimerStats(name: String, timer: Timer) extends AppStats {
  override def toMap: Map[String, String] = {
    val snapshot = timer.snapshot
    SortedMap(
      "Latency.50thPercentile" -> snapshot.getMedian().toString,
      "Latency.75thPercentile" -> snapshot.get75thPercentile().toString,
      "Latency.95thPercentile" -> snapshot.get95thPercentile().toString,
      "Latency.98thPercentile" -> snapshot.get98thPercentile().toString,
      "Latency.99thPercentile" -> snapshot.get99thPercentile().toString,
      "Latency.999thPercentile" -> snapshot.get999thPercentile().toString,
      "Latency.Min" -> timer.min.toString,
      "Latency.Mean" -> timer.mean.toString,
      "Latency.Max" -> timer.max.toString,
      "Latency.StdDev" -> timer.stdDev.toString,
      "RequestCount" -> timer.count.toString,
      "Rate.OneMinute" -> timer.oneMinuteRate.toString,
      "Rate.FiveMinute" -> timer.fiveMinuteRate.toString,
      "Rate.FifteenMinute" -> timer.fifteenMinuteRate.toString)
  }
}

object Stats extends InstrumentedBuilder {
  override val metricRegistry = new MetricRegistry()
  
  private val appTimers = Set("API", "Web")
  private val counters = new ConcurrentHashMap[String,AtomicInteger]()
  
  private val timers = {
    val map = new ConcurrentHashMap[String,Timer]()
    appTimers.foreach { timerName =>
      map.put(timerName, metrics.timer(timerName))
    }
    map
  }

  val StartupTime = new Date()

  def get(): List[AppStats] = {
    val met = metricRegistry.getMetrics.asScala.map { case(metricName, metric) =>
      metric match {
        case timer: Timer => Some(AppTimerStats(metricName, timer))
        case _ => None
      }
    }.foldLeft(List[AppStats]()) { case(total, current) =>
      if (current.isDefined) {
        total ++ List(current.get)
      } else {
        total ++ Nil
      }
    } ++ List(counterStats)
    val partitioned = met.groupBy(s => appTimers.contains(s.name))
    partitioned.get(true).getOrElse(Nil) ++ partitioned.get(false).getOrElse(Nil)
  }

  def time[T](name: String)(f: => T): T = {
    val timer = timers.putIfAbsent(name, metrics.timer(name)) match {
      case null => timers.get(name)
      case t => t
    }
    timer.time {
      f
    }
  }

  def count(namespace: String, name: String): Int = {
    val key = "%s.%s".format(namespace, name)
    counters.putIfAbsent(key, new AtomicInteger(0)) match {
      case counter: AtomicInteger => counter.incrementAndGet
      case null => counters.get(key).incrementAndGet
    }
  }

  def apiRequest[T](f: => T): T = time("API")(f)
  def webRequest[T](f: => T): T = time("Web")(f)
  
  private def counterStats: AppStats = {
    val counterMap: Map[String,String] = counters.asScala.toMap.map { case(k,v) =>
      k -> v.get.toString
    }
    new AppStats {
      val name = "Counters"
      override def toMap = counterMap
    }
  }
}

