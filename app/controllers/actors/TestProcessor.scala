package controllers
package actors

import scala.concurrent.duration._
import util.concurrent.BackgroundProcess

case class TestProcessor(sleepMs: Long, userTimeout: Option[FiniteDuration] = None)
  extends BackgroundProcess[Boolean]
{
  override def defaultTimeout: FiniteDuration = 5 seconds
  val timeout = userTimeout.getOrElse(defaultTimeout)

  def run(): Boolean = {
      println("Sleeping for %d millis".format(sleepMs))
      Thread.sleep(sleepMs)
      println("Done sleeping for %d millins".format(sleepMs))
      true
  }
}
