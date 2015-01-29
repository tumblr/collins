package controllers
package actors

import scala.concurrent.duration.Duration
import play.api.mvc.{AnyContent, Request}
import util.concurrent.BackgroundProcess
import util.plugins.SoftLayer

case class ActivationProcessor(slId: Long, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[Boolean] {
  override def defaultTimeout: Duration = Duration("60 seconds")
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): Boolean = {
    val plugin = SoftLayer.pluginEnabled.get
    plugin.activateServer(slId)()
  }
}

