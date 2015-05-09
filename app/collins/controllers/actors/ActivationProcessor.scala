package collins.controllers.actors

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import play.api.mvc.AnyContent
import play.api.mvc.Request

import collins.softlayer.SoftLayerConfig
import collins.util.concurrent.BackgroundProcess
import collins.util.plugins.SoftLayer

case class ActivationProcessor(slId: Long, userTimeout: Option[FiniteDuration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[Boolean] {
  val timeout = userTimeout.getOrElse(Duration(SoftLayerConfig.activationRequestTimeoutMs, TimeUnit.MILLISECONDS))

  def run(): Boolean = {
    val plugin = SoftLayer.pluginEnabled.get
    Await.result(plugin.activateServer(slId), timeout)
  }
}

