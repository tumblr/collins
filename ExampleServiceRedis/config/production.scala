import com.tumblr.serx.config._
import com.tumblr.logging.config._
import com.twitter.conversions.time._
import com.twitter.logging.config._
import com.twitter.ostrich.admin.config._

// production mode.
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

  admin.statsNodes = new StatsConfig {
    reporters = new TimeSeriesCollectorConfig
  }

  loggers =
    new LoggerConfig {
      level = Level.INFO
      handlers = new FileHandlerConfig {
        filename = "/var/log/serx/production.log"
        roll = Policy.Daily
        formatter = TumblrBasicFormatterConfig       
      }
    } :: new LoggerConfig {
      node = "stats"
      level = Level.INFO
      useParents = false
      handlers = new FileHandlerConfig {
        filename = "/var/log/serx/stats.log"
        roll = Policy.Daily
        formatter = BareFormatterConfig
      }
    }
}
