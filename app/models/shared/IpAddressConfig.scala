package models
package shared

import util.{IpAddress, IpAddressCalc, MessageHelper}
import util.config.{Configurable, ConfigAccessor, ConfigValue, SimpleAddressConfig}

import play.api.Logger
import scala.collection.JavaConverters._
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}

/**
 * Represents an IP Address configuration.
 *
 * An IP Address configuration has 0 or more pools associated with it, along with a default pool. IP
 * address configurations come in two flavors, naked, and pooled.
 *
 * A naked configuration looks like:
 *     key.network="172.16.16.0/24"
 *     key.startAddress="172.16.16.100"
 * A pooled configuration looks like
 *     key.defaultPool="provisioning"
 *     key.pools.provisioning.network="172.16.16.0/24"
 *     key.pools.provisioning.startAddress="172.16.16.100"
 *     key.pools.provisionnig.name="PROVISIONING"
 */
case class IpAddressConfig(cfgName: String, source: SimpleAddressConfig) extends MessageHelper("ip_address") {

  import AddressPool.poolName

  private[this] val logger = Logger("IpAddressConfig-%s".format(cfgName))

  // Whether or not to be strict about address creation, names, etc
  val strict = source.strict

  // PoolName -> AddressPool map, if pools are specified
  val pools: ConcurrentHashMap[String,AddressPool] = new ConcurrentHashMap(
    IpAddressConfig.getAddressPools(source).asJava
  )

  // The default address pool, either one from the pools map, or if no pools were specified, assume
  // a 'naked' config (e.g. network/etc hanging off the key)
  val defaultPool: Option[AddressPool] = source.defaultPoolName.map { pool =>
    Option(pools.get(poolName(pool))).getOrElse(
      throw source.globalError(message("invalidDefaultPool", pool))
    )
  }.orElse(
    AddressPool.fromConfig(source, IpAddressConfig.DefaultPoolName, false, false)
  )

  def hasDefault: Boolean = defaultPool.isDefined
  def hasPool(pool: String): Boolean = pools.containsKey(poolName(pool))
  def pool(name: String): Option[AddressPool] = Option(pools.get(poolName(name)))
  def poolNames: Set[String] = pools.keySet.asScala.toSet
  def defaultPoolName: Option[String] = defaultPool.map(_.name)
  protected def onChange(updatedSource: SimpleAddressConfig) {
    val newPools = IpAddressConfig.getAddressPools(updatedSource)
    newPools.foreach { case(name, addressPool) =>
      if (!pools.containsKey(name)) {
        logger.info("Saw new pool %s".format(name))
        pools.put(name, addressPool)
      } else {
        Option(pools.get(name)).filterNot(_.strictEquals(addressPool)).foreach { _ =>
          logger.info("Pool %s changed".format(name))
          pools.replace(name, addressPool)
        }
      }
    }
    pools.keys().asScala.foreach { name =>
      if (!newPools.contains(name)) {
        logger.info("Pool %s no longer defined".format(name))
        pools.remove(name)
      }
    }
  }
}

object IpAddressConfig extends Configurable {
  import util.config.TypesafeConfiguration
  import AddressPool.poolName

  private val queue = new ConcurrentLinkedQueue[IpAddressConfig]()
  override val namespace = "ipAddresses"
  override val referenceConfigFilename = "ipaddresses_reference.conf"

  val DefaultPoolName = "DEFAULT"

  // Just for testing
  protected[shared] def apply(config: Option[TypesafeConfiguration]): Option[IpAddressConfig] =
    config.map { cfg =>
      val ipCfg = new IpAddressConfig("IpAddressConfig.apply", new SimpleAddressConfig(cfg))
      queue.add(ipCfg)
      ipCfg
    }
  def get(initializing: Boolean = false): Option[IpAddressConfig] = underlying.map { cfg =>
    val ipCfg = new IpAddressConfig("IpAddressConfig.get", new SimpleAddressConfig(cfg))
    if (!initializing) {
      queue.add(ipCfg)
    }
    ipCfg
  }

  override protected def validateConfig() {
    get(true)
  }
  protected def getAddressPools(config: SimpleAddressConfig): Map[String,AddressPool] = {
    config.pools.map { case(name, poolCfg) =>
      val simpleConfig = SimpleAddressConfig(poolCfg.toConfig, Some(name), Some(config.strict))
      val addressPool = AddressPool.fromConfig(simpleConfig, name, true, config.strict).get
      poolName(addressPool.name) -> addressPool
    }.toMap
  }
  override protected def afterChange() {
    underlying.foreach { cfg =>
      queue.asScala.foreach(_.onChange(new SimpleAddressConfig(cfg)))
    }
  }
}
