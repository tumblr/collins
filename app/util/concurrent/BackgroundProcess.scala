package util
package concurrent

import akka.util.Duration
import java.util.concurrent.TimeUnit

trait BackgroundProcess[T] {
  val timeout: Duration
  def run(): T

  protected def defaultTimeout: Duration = Duration(ConcurrencyConfig.timeoutMs, TimeUnit.MILLISECONDS)
}
