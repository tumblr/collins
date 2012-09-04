package controllers
package actors

import akka.util.Duration
import collins.provisioning.ProvisionerRequest
import collins.shell.CommandResult
import com.twitter.util.Future
import play.api.mvc.{AnyContent, Request}
import util.Provisioner
import util.concurrent.BackgroundProcess

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

case class ProvisionerTest(request: ProvisionerRequest, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[ProvisionerResult]
{
  override def defaultTimeout: Duration = Duration.parse("90 seconds")
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): ProvisionerResult = Provisioner.pluginEnabled { plugin =>
    plugin.test(request).map { res =>
      if (res.exitCode == 0)
        ProvisionerResult(ProvisionerStatus.TestSucceeded, res)
      else
        ProvisionerResult(ProvisionerStatus.TestFailed, res)
    }.get()
  }.getOrElse(ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled")))
}

case class ProvisionerRun(request: ProvisionerRequest, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[ProvisionerResult]
{
  override def defaultTimeout: Duration = Duration.parse("90 seconds")
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): ProvisionerResult = Provisioner.pluginEnabled { plugin =>
    plugin.provision(request).map { cmd =>
      ProvisionerResult(ProvisionerStatus.CommandExecuted, cmd)
    }.get()
  }.getOrElse(ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled")))
}
