package controllers

import akka.actor.Actor
import Actor._
import akka.dispatch.FutureTimeoutException
import akka.util.Duration
import akka.util.duration._

import play.api.libs.akka._
import play.api.libs.concurrent.{Redeemed, Thrown}
import play.api.mvc.{AnyContent, Request}

case class AssetUpdateProcessor(tag: String, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent])
  extends BackgroundProcess[Either[ResponseData,Boolean]]
{
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): Either[ResponseData,Boolean] = {
    val assetUpdater = actions.UpdateAsset.get()
    assetUpdater.execute(tag)
  }
}

sealed trait BackgroundProcess[T] {
  val timeout: Duration
  def run(): T

  protected def defaultTimeout: Duration = {
    val config = util.Helpers.subAsMap("")
    Duration.parse(config.getOrElse("timeout", "2 seconds"))
  }
}

private[controllers] class BackgroundProcessor extends Actor {
  def receive = {
    case processor: controllers.AssetUpdateProcessor => self.reply(processor.run())
  }
}

object BackgroundProcessor {
  lazy val ref = actorOf[BackgroundProcessor].start()

  type SendType[T] = Tuple2[Option[Throwable], Option[T]]
  def send[PROC_RES,RESPONSE](cmd: BackgroundProcess[PROC_RES])(result: SendType[PROC_RES] => RESPONSE)(implicit mf: Manifest[PROC_RES]) = {
    ref.?(cmd)(timeout = cmd.timeout).mapTo[PROC_RES].asPromise.extend1 {
      case Redeemed(v) => result(Tuple2(None, Some(v)))
      case Thrown(e) => e match {
        case t: FutureTimeoutException =>
          val ex = new Exception("Command took longer than %d seconds: %s".format(
            cmd.timeout.toSeconds, t.getMessage
          ))
          result(Tuple2(Some(ex), None))
        case _ =>
          result(Tuple2(Some(e), None))
      }
    }
  }
}
