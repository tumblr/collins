package collins.controllers.actors

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

import collins.provisioning.Provisioner
import collins.provisioning.ProvisionerConfig
import collins.provisioning.ProvisionerRequest
import collins.shell.CommandResult
import collins.util.concurrent.BackgroundProcess

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
    if (ProvisionerConfig.enabled) {
      val res = Provisioner.test(request)
      if (res.exitCode == 0)
        ProvisionerResult(ProvisionerStatus.TestSucceeded, res)
      else
        ProvisionerResult(ProvisionerStatus.TestFailed, res)
    } else {
      ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled"))
    }
  }
}

case class ProvisionerRun(request: ProvisionerRequest, userTimeout: Option[FiniteDuration] = None) extends BackgroundProcess[ProvisionerResult]
{
  val timeout = userTimeout.getOrElse(Duration(ProvisionerConfig.commandTimeoutMs, TimeUnit.MILLISECONDS))

  def run(): ProvisionerResult = {
    if(ProvisionerConfig.enabled) {
  	  val cmd = Provisioner.provision(request)
  	  ProvisionerResult(ProvisionerStatus.CommandExecuted, cmd)
    } else {
      ProvisionerResult(ProvisionerStatus.PluginDisabled, CommandResult(-2, "Provisioner plugin not enabled"))
    }
  }
}
