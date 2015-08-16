package collins.hazelcast

import play.api.Logger

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
      val jc = config.getNetworkConfig().getJoin()
      jc.getTcpIpConfig().setEnabled(true).addMember(HazelcastConfig.members)
      logger.trace(f"Instantiating hazelcast instance using members ${HazelcastConfig.members}%s")
      instance = Some(Hazelcast.newHazelcastInstance(config))
    }
  }

  def getCache() = {
    instance.map(_.getMap[String, AnyRef]("cache"))
  }

  def terminateHazelcast() {
    Hazelcast.shutdownAll()
  }
}