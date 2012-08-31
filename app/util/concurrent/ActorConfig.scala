package util
package concurrent

import config.Configurable

object ActorConfig extends Configurable {
  override val namespace = "concurrency"
  override val referenceConfigFilename = "concurrency_reference.conf"

  val DefaultActorCount = Runtime.getRuntime().availableProcessors()*2
  lazy val ActorCount = getInt("actorCount").filter(_ > 0).getOrElse(DefaultActorCount)

  override protected def validateConfig() {
    ActorCount
  }
}
