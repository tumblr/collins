package models
package shared

import util.{IpAddress, IpAddressCalc, MessageHelper}
import util.concurrent.LockingBitSet
import util.config.{Configurable, ConfigAccessor, ConfigValue, SimpleAddressConfig}

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
case class IpAddressConfig(source: SimpleAddressConfig) extends MessageHelper("ip_address") {

  import AddressPool.poolName

  // Whether or not to be strict about address creation, names, etc
  val strict = source.strict

  // PoolName -> AddressPool map, if pools are specified
  val pools: Map[String,AddressPool] = source.pools.map { case(name, poolCfg) =>
    val simpleConfig = SimpleAddressConfig(poolCfg.toConfig, Some(name), Some(strict))
    val addressPool = AddressPool.fromConfig(simpleConfig, name, true, strict).get
    poolName(addressPool.name) -> addressPool
  }.toMap

  // The default address pool, either one from the pools map, or if no pools were specified, assume
  // a 'naked' config (e.g. network/etc hanging off the key)
  val defaultPool: Option[AddressPool] = source.defaultPoolName.map { pool =>
    pools.get(poolName(pool)).getOrElse(
      throw source.globalError(message("invalidDefaultPool", pool))
    )
  }.orElse(
    AddressPool.fromConfig(source, IpAddressConfig.DefaultPoolName, false, false)
  )

  def hasDefault: Boolean = defaultPool.isDefined
  def hasPool(pool: String): Boolean = pools.contains(poolName(pool))
  def pool(name: String): Option[AddressPool] = pools.get(poolName(name))
  def poolNames: Set[String] = pools.keySet
  def defaultPoolName: Option[String] = defaultPool.map(_.name)
}

object IpAddressConfig extends Configurable {
  import util.config.TypesafeConfiguration

  override val namespace = "ipAddresses"
  override val referenceConfigFilename = "ipaddresses_reference.conf"

  val DefaultPoolName = "DEFAULT"

  def apply(config: Option[TypesafeConfiguration]): Option[IpAddressConfig] =
    config.map(cfg => new IpAddressConfig(new SimpleAddressConfig(cfg)))
  def get(): Option[IpAddressConfig] = underlying.map { cfg =>
    new IpAddressConfig(new SimpleAddressConfig(cfg))
  }

  override protected def validateConfig() {
    get
  }
}


