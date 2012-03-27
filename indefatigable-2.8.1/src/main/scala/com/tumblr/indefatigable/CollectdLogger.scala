package com.tumblr.indefatigable

import com.twitter.logging.config.HandlerConfig
import com.twitter.logging.{Level, Formatter, Handler}
import java.util.logging.LogRecord
import org.collectd.api.Collectd

class CollectdLogger extends HandlerConfig {
  def apply() = new CollectdHandler(formatter(), level)
}

class CollectdHandler(
    formatter: Formatter,
    level: Option[Level]
) extends Handler(formatter, level) {
  def publish(rec: LogRecord) {
    val msg = formatter.format(rec)
    rec.getLevel match {
      case Level.ERROR =>   Collectd.logError(msg)
      case Level.WARNING => Collectd.logWarning(msg)
      case Level.INFO =>    Collectd.logInfo(msg)
      case Level.DEBUG =>   Collectd.logDebug(msg)
      case Level.TRACE =>   Collectd.logNotice(msg)

      case _           =>   Collectd.logInfo(msg)
    }
  }

  def flush() {
    // do nothing
  }

  def close() {
    // do nothing
  }
} 
