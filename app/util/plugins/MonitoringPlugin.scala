package util.plugins
import play.api.Application
import play.api.Play

object MonitoringPlugin {
  def option(): Option[collins.monitoring.MonitoringPlugin] = {
    println("-----------------------")
    Play.maybeApplication.flatMap { app =>
      app.plugin[collins.monitoring.MonitoringPlugin].filter(_.enabled)
    }
  }
}
