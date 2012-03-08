package controllers
package actors

import akka.actor.Actor._
import akka.actor.Actor
import akka.dispatch.FutureTimeoutException
import akka.routing.Routing.loadBalancerActor
import akka.routing.CyclicIterator

import play.api.libs.akka._
import play.api.libs.concurrent.{Redeemed, Thrown}

import util.Helpers

private[actors] class BackgroundProcessor extends Actor {
  def receive = {
    case processor: ActivationProcessor => self.reply(processor.run())
    case processor: AssetUpdateProcessor => self.reply(processor.run())
    case processor: AssetCancelProcessor => self.reply(processor.run())
    case processor: ProvisionerProcessor => self.reply(processor.run())
    case processor: TestProcessor => self.reply(processor.run())
  }
}

object BackgroundProcessor {
  val DefaultActorCount = Runtime.getRuntime().availableProcessors()*2
  val actorCount = Helpers.subAsMap("").get("actorCount").map(_.toInt).getOrElse(DefaultActorCount)
  val ref = loadBalancerActor(
    new CyclicIterator((1 to actorCount)
      .map(_ => actorOf[BackgroundProcessor].start())
      .toList
    )
  )

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
