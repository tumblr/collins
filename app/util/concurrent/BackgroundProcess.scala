package util
package concurrent

import scala.concurrent.duration.FiniteDuration

trait BackgroundProcess[T] {
  val timeout: FiniteDuration
  def run(): T
}
