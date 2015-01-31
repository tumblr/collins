package controllers
package actors

import scala.concurrent.duration._
import collins.provisioning.ProvisionerRequest
import collins.shell.CommandResult
import com.twitter.util.Future
import play.api.mvc.{AnyContent, Request}
import util.concurrent.BackgroundProcess
import util.plugins.Provisioner
import com.twitter.util.Await

sealed trait ProvisionerStatus
sealed trait ProvisionerFailure extends ProvisionerStatus
sealed trait ProvisionerSuccess extends ProvisionerStatus
object ProvisionerStatus {
  case object PluginDisabled extends ProvisionerFailure
  case object TestFailed extends ProvisionerFailure
  case object TestSucceeded extends ProvisionerSuccess
  case object CommandExecuted extends ProvisionerStatus
  case object NoResultData extends ProvisionerFailure
}

case class ProvisionerResult(status: ProvisionerStatus, commandResult: CommandResult)

case class ProvisionerTest(request: ProvisionerRequest, userTimeout: Option[FiniteDuration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[ProvisionerResult]
{
  override def defaultTimeout = 90 seconds
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): ProvisionerResult = Provisioner.pluginEnabled { plugin =>
    Await.result(plugin.test(request).map { res =>
      if (res.exitCode == 0)
        ProvisionerResult(ProvisionerStatus.TestSucceeded, res)
      else
        ProvisionerResult(ProvisionerStatus.TestFailed, res)
    })
  }.getOrElse(ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled")))
}

case class ProvisionerRun(request: ProvisionerRequest, userTimeout: Option[FiniteDuration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[ProvisionerResult]
{
  override def defaultTimeout = 90 seconds
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): ProvisionerResult = Provisioner.pluginEnabled { plugin =>
    Await.result(plugin.provision(request).map { cmd =>
      ProvisionerResult(ProvisionerStatus.CommandExecuted, cmd)
    })
  }.getOrElse(ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled")))
}
