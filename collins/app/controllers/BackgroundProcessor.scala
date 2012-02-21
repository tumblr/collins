package controllers

import models.{AssetLifecycle, AssetLog, MetaWrapper, Model, Status => AStatus}
import util.{Provisioner, SoftLayer}
import com.tumblr.play.ProvisionerRequest

import akka.actor.Actor._
import akka.actor.Actor
import akka.dispatch.FutureTimeoutException
import akka.routing._
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

case class ProvisionerProcessor(request: ProvisionerRequest, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent]) extends BackgroundProcess[Int]
{
  override def defaultTimeout: Duration = Duration.parse("60 seconds")
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): Int = {
    Provisioner.pluginEnabled { plugin =>
      plugin.provision(request)()
    }.getOrElse(-2)
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

private[controllers] class BackgroundProcessor extends Actor with DefaultActorPool
    with BoundedCapacityStrategy
    with ActiveFuturesPressureCapacitor
    with SmallestMailboxSelector
    with BasicNoBackoffFilter
{
  def receive = _route
  def lowerBound = 2
  def upperBound = 4
  def rampupRate = 0.1
  def partialFill = true
  def selectionCount = 1

  def instance = actorOf(new Actor {def receive = {
    case processor: controllers.AssetUpdateProcessor => self.reply(processor.run())
    case processor: controllers.AssetCancelProcessor => self.reply(processor.run())
    case processor: controllers.ProvisionerProcessor => self.reply(processor.run())
  }})
}

object BackgroundProcessor {
  val ref = actorOf[BackgroundProcessor].start()

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

case class AssetCancelProcessor(tag: String, userTimeout: Option[Duration] = None)(implicit req: Request[AnyContent])
  extends BackgroundProcess[Either[ResponseData,Long]]
{
  override def defaultTimeout: Duration =
    Duration.parse("10 seconds")

  val timeout = userTimeout.getOrElse(defaultTimeout)
  def run(): Either[ResponseData,Long] = {
    req.body.asUrlFormEncoded.flatMap(_.get("reason")).flatMap(_.headOption).map(_.trim).filter(_.size > 0).map { _reason =>
      val reason = _reason.trim
      Api.withAssetFromTag(tag) { asset =>
        SoftLayer.pluginEnabled.map { plugin =>
          plugin.softLayerId(asset) match {
            case None =>
              Left(Api.getErrorMessage("Asset is not a softlayer asset"))
            case Some(n) =>
              plugin.cancelServer(n, reason)() match {
                case 0L =>
                  Left(Api.getErrorMessage("There was an error cancelling this server"))
                case ticketId =>
                  Model.withTransaction { implicit con =>
                    MetaWrapper.createMeta(asset, Map("CANCEL_TICKET" -> ticketId.toString))
                    AssetLifecycle.updateAssetStatus(asset, Map(
                      "status" -> AStatus.Enum.Cancelled.toString,
                      "reason" -> reason
                    ), con)
                    AssetLog.informational(asset, "User requested server cancellation",
                      AssetLog.Formats.PlainText, AssetLog.Sources.Internal).create()
                  }
                  plugin.setNote(n, "Cancelled: %s".format(reason))()
                  Right(ticketId)
              }
          }
        }.getOrElse {
          Left(Api.getErrorMessage("SoftLayer plugin is not enabled"))
        }
      }
    }.getOrElse(Left(Api.getErrorMessage("No reason specified for cancellation")))
  }
}
