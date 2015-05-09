package collins.controllers.actors

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import collins.util.concurrent.BackgroundProcess

case class TestProcessor(sleepMs: Long, userTimeout: Option[FiniteDuration] = None)
  extends BackgroundProcess[Boolean]
{
  val timeout = userTimeout.getOrElse(5 seconds)

  def run(): Boolean = {
      println("Sleeping for %d millis".format(sleepMs))
      Thread.sleep(sleepMs)
      println("Done sleeping for %d millins".format(sleepMs))
      true
  }
}
