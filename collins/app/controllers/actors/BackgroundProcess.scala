package controllers
package actors

import akka.util.Duration
import util.Config

trait BackgroundProcess[T] {
  val timeout: Duration
  def run(): T

  protected def defaultTimeout: Duration = {
    val config = Config.toMap
    Duration.parse(config.getOrElse("timeout", "2 seconds"))
  }
}
