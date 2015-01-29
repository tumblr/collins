package util
package concurrent

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

trait BackgroundProcess[T] {
  val timeout: Duration
  def run(): T

  protected def defaultTimeout: Duration = Duration(ConcurrencyConfig.timeoutMs, TimeUnit.MILLISECONDS)
}
