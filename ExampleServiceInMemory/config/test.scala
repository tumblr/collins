import com.tumblr.serx.config._
import com.tumblr.logging.config._
import com.twitter.conversions.time._
import com.twitter.logging.config._
import com.twitter.ostrich.admin.config._

// test mode.
new SerxServiceConfig {

  // Add your own config here

  // Where your service will be exposed.
  thriftPort = 9999

  // Ostrich http admin port.  Curl this for stats, etc
  admin.httpPort = 9900

  // End user configuration

  // Ostrich stats and logger configuration.
  admin.statsNodes = new StatsConfig {
    reporters = new JsonStatsLoggerConfig {
      loggerName = "stats"
      serviceName = "serx"
    } :: new TimeSeriesCollectorConfig
  }

  loggers = 
    new LoggerConfig {
      level = Level.WARNING
      handlers = new ConsoleHandlerConfig {
        formatter = TumblrBasicFormatterConfig
      }
    } :: new LoggerConfig {
      node = "stats"
      level = Level.FATAL
      useParents = false
      handlers = new ConsoleHandlerConfig
    }

}
