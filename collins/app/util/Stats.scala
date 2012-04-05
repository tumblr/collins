package util

import com.yammer.metrics.scala.Instrumented
import com.yammer.metrics.core.Timer

import java.util.Date
import scala.collection.JavaConverters._
import scala.collection.immutable.SortedMap

case class AppStats(name: String, timer: Timer) {
  def toMap: Map[String, String] = SortedMap(
    "Latency.50thPercentile" -> timer.getSnapshot().getMedian().toString,
    "Latency.75thPercentile" -> timer.getSnapshot().get75thPercentile().toString,
    "Latency.95thPercentile" -> timer.getSnapshot().get95thPercentile().toString,
    "Latency.98thPercentile" -> timer.getSnapshot().get98thPercentile().toString,
    "Latency.99thPercentile" -> timer.getSnapshot().get99thPercentile().toString,
    "Latency.999thPercentile" -> timer.getSnapshot().get999thPercentile().toString,
    "Latency.Min" -> timer.min().toString,
    "Latency.Mean" -> timer.mean().toString,
    "Latency.Max" -> timer.max().toString,
    "Latency.StdDev" -> timer.stdDev().toString,
    "Latency.Unit" -> timer.durationUnit().toString,
    "RequestCount" -> timer.count().toString,
    "Rate.OneMinute" -> timer.oneMinuteRate().toString,
    "Rate.FiveMinute" -> timer.fiveMinuteRate().toString,
    "Rate.FifteenMinute" -> timer.fifteenMinuteRate().toString,
    "Rate.Unit" -> timer.rateUnit().toString
  )
}

object Stats extends Instrumented {
  private val api = metrics.timer("API")
  private val web = metrics.timer("Web")

  val StartupTime = new Date()

  def get(): List[AppStats] = {
    val met = metricsRegistry.allMetrics.asScala.map { case(metricName, metric) =>
      val name = metricName.getName()
      if (metric.isInstanceOf[Timer]) {
        Some(AppStats(name, metric.asInstanceOf[Timer]))
      } else {
        None
      }
    }.foldLeft(List[AppStats]()) { case(total, current) =>
      if (current.isDefined) {
        total ++ List(current.get)
      } else {
        total ++ Nil
      }
    }
    met
  }

  def apiRequest[T](f: => T): T = api.time {
    f
  }
  def webRequest[T](f: => T): T = web.time {
    f
  }
}

