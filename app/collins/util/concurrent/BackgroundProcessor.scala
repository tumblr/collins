package collins.util.concurrent

import java.util.concurrent.TimeoutException

import scala.Left
import scala.Right
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import akka.actor.Actor
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout.durationToTimeout

class BackgroundProcessorActor extends Actor {
  def receive = {
    case processor: BackgroundProcess[_] => sender ! processor.run()
  }
}

case class SexyTimeoutException(timeout: Duration) extends Exception("Command timeout after %s seconds".format(timeout.toSeconds.toString)) {
  override def toString: String = {
    this.getMessage
  }
}
object BackgroundProcessor {
  import play.api.Play.current

  lazy val ref = Akka.system.actorOf(Props[BackgroundProcessorActor].
    withRouter(FromConfig()), name = "background-processor")

  type SendType[T] = Either[Throwable, T]

  def send[PROC_RES, RESPONSE](cmd: BackgroundProcess[PROC_RES])(result: SendType[PROC_RES] => RESPONSE)(implicit mf: Manifest[PROC_RES]): Future[RESPONSE] = {

    val f: Future[PROC_RES] = ask(ref, cmd)(cmd.timeout).mapTo[PROC_RES]

    val mpd: Future[RESPONSE] = f.map { x => result(Right(x)) }
    mpd.recover {
      case t: TimeoutException => result(Left(SexyTimeoutException(cmd.timeout)))
      case th: Throwable       => result(Left(th))
    }
  }

  //possibly the WORST name ever.  Don't judge me.  'flatSend' because the above function is really just a fancy map function
  //since you pass a func that goes from x->y and get a future[y], this is just the flatMap version of that.
  def flatSend[PROC_RES, RESPONSE](cmd: BackgroundProcess[PROC_RES])(result: SendType[PROC_RES] => Future[RESPONSE])(implicit mf: Manifest[PROC_RES]): Future[RESPONSE] = {

    val f: Future[PROC_RES] = ask(ref, cmd)(cmd.timeout).mapTo[PROC_RES]
    val mpd: Future[RESPONSE] = f.flatMap { x => result(Right(x)) }
    mpd.recoverWith {
      case t: TimeoutException => result(Left(SexyTimeoutException(cmd.timeout)))
      case th: Throwable       => result(Left(th))
    }
  }
}
