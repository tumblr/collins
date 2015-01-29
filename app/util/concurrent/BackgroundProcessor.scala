package util
package concurrent

import akka.util.Timeout.durationToTimeout
import akka.actor._
import akka.pattern.ask
import akka.routing.RoundRobinRouter
import scala.concurrent.duration.Duration

import play.api.libs.concurrent._

import java.util.concurrent.TimeoutException
import play.api.libs.concurrent.Execution.Implicits._


class BackgroundProcessorActor extends Actor {
  def receive = {
    case processor: BackgroundProcess[_] => sender ! processor.run()
  }
}

case class SexyTimeoutException(timeout: Duration) extends Exception("Command timeout after %s seconds".format(timeout.toSeconds.toString)) {
  override def toString(): String = {
    this.getMessage()
  }
}
object BackgroundProcessor {
  import play.api.Play.current

  lazy val ref = {
    val routees = (0 until 128).map { _ =>
      Akka.system.actorOf(Props[BackgroundProcessorActor])
    }
    Akka.system.actorOf(
      Props[BackgroundProcessorActor].withRouter(RoundRobinRouter(routees))
    )
  }

  type SendType[T] = Tuple2[Option[Throwable], Option[T]]
  def send[PROC_RES,RESPONSE](cmd: BackgroundProcess[PROC_RES])(result: SendType[PROC_RES] => RESPONSE)(implicit mf: Manifest[PROC_RES]) = {
    ask(ref, cmd)(cmd.timeout).mapTo[PROC_RES].map {
      case Redeemed(v: PROC_RES) => result(Tuple2(None, Some(v)))
      case Thrown(e) => e match {
        case t: TimeoutException =>
          result(Tuple2(Some(SexyTimeoutException(cmd.timeout)), None))
        case _ =>
          result(Tuple2(Some(e), None))
      }
    }
  }
}
