package util
package concurrent

import akka.actor._
import akka.pattern.ask
import akka.routing.RoundRobinRouter
import akka.util.Duration

import play.api.libs.concurrent._

import java.util.concurrent.TimeoutException
//import scala.collection.immutable.Vector

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
    ask(ref, cmd)(cmd.timeout).mapTo[PROC_RES].asPromise.extend1 {
      case Redeemed(v) => result(Tuple2(None, Some(v)))
      case Thrown(e) => e match {
        case t: TimeoutException =>
          result(Tuple2(Some(SexyTimeoutException(cmd.timeout)), None))
        case _ =>
          result(Tuple2(Some(e), None))
      }
    }
  }
}
