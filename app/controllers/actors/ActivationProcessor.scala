package controllers
package actors

import scala.concurrent.duration._
import play.api.mvc.{AnyContent, Request}
import util.concurrent.BackgroundProcess
import util.plugins.SoftLayer
import scala.concurrent.{Await, Future}
import java.util.concurrent.TimeUnit
import collins.softlayer.SoftLayerConfig

case class ActivationProcessor(slId: Long, userTimeout: Option[FiniteDuration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[Boolean] {
  val timeout = userTimeout.getOrElse(Duration(SoftLayerConfig.activationRequestTimeoutMs, TimeUnit.MILLISECONDS))

  def run(): Boolean = {
    val plugin = SoftLayer.pluginEnabled.get
    Await.result(plugin.activateServer(slId), timeout)
  }
}

