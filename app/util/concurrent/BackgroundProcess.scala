package util
package concurrent

import akka.util.Duration

trait BackgroundProcess[T] {
  val timeout: Duration
  def run(): T

  protected def defaultTimeout: Duration = {
    val config = Config.toMap
    Duration.parse(config.getOrElse("timeout", "2 seconds"))
  }
}
