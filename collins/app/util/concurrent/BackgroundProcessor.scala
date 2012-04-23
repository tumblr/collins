package util
package concurrent

import akka.actor.Actor._
import akka.actor.Actor
import akka.dispatch.FutureTimeoutException
import akka.routing.Routing.loadBalancerActor
import akka.routing.CyclicIterator

import play.api.libs.akka._
import play.api.libs.concurrent.{Redeemed, Thrown}

private[concurrent] class BackgroundProcessor extends Actor {
  def receive = {
    case processor: BackgroundProcess[_] => self.reply(processor.run())
  }
}

case class SexyTimeout(timeout: Duration) extends Exception(message) {
  override def toString(): String = {
    "Command timeout after %d seconds".format(timeout.toSeconds.toString)
  }
}
object BackgroundProcessor {
  val ref = loadBalancerActor(
    new CyclicIterator((1 to ActorConfig.ActorCount)
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
          result(Tuple2(Some(SexyTimeoutException(cmd.timeout)), None))
        case _ =>
          result(Tuple2(Some(e), None))
      }
    }
  }
}
