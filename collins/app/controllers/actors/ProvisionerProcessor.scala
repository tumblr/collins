package controllers
package actors

import akka.util.Duration
import com.tumblr.play.{CommandResult, ProvisionerRequest}
import com.twitter.util.Future
import play.api.mvc.{AnyContent, Request}
import util.Provisioner
import util.concurrent.BackgroundProcess

case class ProvisionerProcessor(request: ProvisionerRequest, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[CommandResult]
{
  override def defaultTimeout: Duration = Duration.parse("90 seconds")
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): CommandResult = {
    Provisioner.pluginEnabled { plugin =>
      val future = plugin.test(request).flatMap { res =>
        if (res.exitCode == 0) {
          plugin.provision(request)
        } else {
          Future(res)
        }
      }
      future()
    }.getOrElse(CommandResult(-2, "Provisioner plugin not enabled"))
  }
}


