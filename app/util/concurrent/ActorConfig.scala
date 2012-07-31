package util
package concurrent

object ActorConfig {
  val DefaultActorCount = Runtime.getRuntime().availableProcessors()*2
  val ActorCount = Config.toMap.get("actorCount").map(_.toInt).getOrElse(DefaultActorCount)
}
