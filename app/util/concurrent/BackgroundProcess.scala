package util
package concurrent

import scala.concurrent.duration.{Duration, FiniteDuration}
import java.util.concurrent.TimeUnit

trait BackgroundProcess[T] {
  val timeout: FiniteDuration
  def run(): T

  protected def defaultTimeout: Duration = Duration(ConcurrencyConfig.timeoutMs, TimeUnit.MILLISECONDS)
}
