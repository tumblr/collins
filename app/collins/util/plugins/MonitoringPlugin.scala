package collins.util.plugins

import play.api.Play

object MonitoringPlugin {
  def option(): Option[collins.monitoring.MonitoringPlugin] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[collins.monitoring.MonitoringPlugin].filter(_.enabled)
    }
  }
}
