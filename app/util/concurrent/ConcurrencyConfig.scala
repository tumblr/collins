package util
package concurrent

import config.Configurable

object ConcurrencyConfig extends Configurable {
  override val namespace = "concurrency"
  override val referenceConfigFilename = "concurrency_reference.conf"

  val DefaultActorCount = Runtime.getRuntime().availableProcessors()*2
  lazy val ActorCount = getInt("actorCount").filter(_ > 0).getOrElse(DefaultActorCount)
  def timeoutMs = getMilliseconds("timeout").getOrElse(2000L)

  override protected def validateConfig() {
    ActorCount
    timeoutMs
  }
}
