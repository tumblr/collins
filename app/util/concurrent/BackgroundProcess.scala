package util
package concurrent

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait BackgroundProcess[T] {
  val timeout: FiniteDuration
  def run(): T

  protected def defaultTimeout: Duration = Duration(ConcurrencyConfig.timeoutMs, TimeUnit.MILLISECONDS)
}
