package collins.metrics

import play.api.Application
import play.api.Logger
import play.api.Plugin

import com.addthis.metrics.reporter.config.ReporterConfig

object MetricsReporter
{
  def setupMetrics() {
    try {
      if (MetricsReporterConfig.enabled) {
        Logger.info("Trying to load metrics-reporter-config...")
        ReporterConfig.loadFromFileAndValidate(MetricsReporterConfig.configFile).enableAll()
      }
    }
    catch {
      case e: Exception => Logger.warn("Failed to load metrics-reporter-config", e)
    }
  }
}
