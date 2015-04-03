package controllers
package actors

import scala.concurrent.duration.FiniteDuration
import collins.provisioning.ProvisionerRequest
import collins.shell.CommandResult
import util.concurrent.BackgroundProcess
import util.plugins.Provisioner
import scala.concurrent.duration.Duration
import collins.provisioning.ProvisionerConfig
import java.util.concurrent.TimeUnit

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

case class ProvisionerTest(request: ProvisionerRequest, userTimeout: Option[FiniteDuration] = None) extends BackgroundProcess[ProvisionerResult]
{
  val timeout = userTimeout.getOrElse(Duration(ProvisionerConfig.checkCommandTimeoutMs, TimeUnit.MILLISECONDS))
  
  def run(): ProvisionerResult = {
    Provisioner.pluginEnabled { plugin =>
      val res = plugin.test(request)
      if (res.exitCode == 0)
        ProvisionerResult(ProvisionerStatus.TestSucceeded, res)
      else
        ProvisionerResult(ProvisionerStatus.TestFailed, res)
    }.getOrElse(ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled")))
  }
}

case class ProvisionerRun(request: ProvisionerRequest, userTimeout: Option[FiniteDuration] = None) extends BackgroundProcess[ProvisionerResult]
{
  val timeout = userTimeout.getOrElse(Duration(ProvisionerConfig.commandTimeoutMs, TimeUnit.MILLISECONDS))

  def run(): ProvisionerResult = {
    Provisioner.pluginEnabled { plugin =>
	  val cmd = plugin.provision(request)
	  ProvisionerResult(ProvisionerStatus.CommandExecuted, cmd)
    }.getOrElse(ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled")))
  }
}
