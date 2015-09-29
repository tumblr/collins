package collins.hazelcast

import play.api.Logger
import play.api.libs.json.JsValue

import com.hazelcast.config.FileSystemXmlConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance

object HazelcastHelper {
  private val logger = Logger(getClass)

  @volatile private[this] var instance: Option[HazelcastInstance] = None

  def setupHazelcast() {
    logger.info(s"Initializing hazelcast, enabled - ${HazelcastConfig.enabled}")
    if (HazelcastConfig.enabled) {
      val config = new FileSystemXmlConfig(HazelcastConfig.configFile)

      if (!HazelcastConfig.members.isEmpty()) {
        val jc = config.getNetworkConfig().getJoin()
        jc.getTcpIpConfig().setEnabled(true).addMember(HazelcastConfig.members)
        logger.trace(f"Instantiating hazelcast instance using members ${HazelcastConfig.members}%s")
      } else {
        logger.warn("Instantiating hazelcast instance on single node, on recommended use case for this deployment is for events.")
      }
      instance = Some(Hazelcast.newHazelcastInstance(config))
    }
  }

  def getCache() = {
    instance.map(_.getMap[String, AnyRef]("cache"))
  }

  def getTopic() = {
    instance.map(_.getReliableTopic[JsValue]("firehose"))
  }

  def terminateHazelcast() {
    Hazelcast.shutdownAll()
    instance = None
  }
}