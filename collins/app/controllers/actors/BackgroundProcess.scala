package controllers
package actors

import akka.util.Duration
import util.Helpers

trait BackgroundProcess[T] {
  val timeout: Duration
  def run(): T

  protected def defaultTimeout: Duration = {
    val config = Helpers.subAsMap("")
    Duration.parse(config.getOrElse("timeout", "2 seconds"))
  }
}
