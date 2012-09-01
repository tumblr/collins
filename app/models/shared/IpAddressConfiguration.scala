package models
package shared

import util.config.{Configurable, ConfigurationAccessor, ConfigValue, TypesafeConfig}
import util.{IpAddress, IpAddressCalc, MessageHelper}
import util.concurrent.LockingBitSet

case class SimpleAddressConfig(
  cfg: TypesafeConfig,
  orName: Option[String] = None,
  orStrict: Option[Boolean] = None
) extends ConfigurationAccessor {
  import AddressPool.poolName

  implicit val configVal = ConfigValue.Optional

  override protected def underlying = Some(cfg)
  override protected def underlying_=(config: Option[TypesafeConfig]) {
  }

  // Default pool to use, if configured, hidden since we may end up with a nake config which will
  // still end up with the DefaultPoolName
  def defaultPoolName: Option[String] = getString("defaultPoolName").map(poolName(_)).filter(_.nonEmpty)
  def name = getString("name").orElse(orName)
  def strict = orStrict.orElse(getBoolean("strict")).getOrElse(true)
  def pools = getObjectMap("pools")
  def startAddress = getString("startAddress")
  def gateway = getString("gateway")
  def network = getString("network")
}

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
case class IpAddressConfiguration(source: SimpleAddressConfig) extends MessageHelper("ip_address") {

  import AddressPool.poolName

  // Whether or not to be strict about address creation, names, etc
  val strict = source.strict

  // PoolName -> AddressPool map, if pools are specified
  val pools: Map[String,AddressPool] = source.pools.map { case(name, poolCfg) =>
    val simpleConfig = SimpleAddressConfig(poolCfg.toConfig, Some(name), Some(strict))
    val addressPool = AddressPool.fromConfiguration(simpleConfig, name, true, strict).get
    poolName(addressPool.name) -> addressPool
  }.toMap

  // The default address pool, either one from the pools map, or if no pools were specified, assume
  // a 'naked' config (e.g. network/etc hanging off the key)
  val defaultPool: Option[AddressPool] = source.defaultPoolName.map { pool =>
    pools.get(poolName(pool)).getOrElse(
      throw source.globalError(message("invalidDefaultPool", pool))
    )
  }.orElse(
    AddressPool.fromConfiguration(source, IpAddressConfiguration.DefaultPoolName, false, false)
  )

  def hasDefault: Boolean = defaultPool.isDefined
  def hasPool(pool: String): Boolean = pools.contains(poolName(pool))
  def pool(name: String): Option[AddressPool] = pools.get(poolName(name))
  def poolNames: Set[String] = pools.keySet
  def defaultPoolName: Option[String] = defaultPool.map(_.name)
}

object IpAddressConfiguration extends Configurable {
  override val namespace = "ipAddresses"
  override val referenceConfigFilename = "ipaddresses_reference.conf"

  val DefaultPoolName = "DEFAULT"

  def apply(config: Option[TypesafeConfig]): Option[IpAddressConfiguration] =
    config.map(cfg => new IpAddressConfiguration(new SimpleAddressConfig(cfg)))
  def get(): Option[IpAddressConfiguration] = underlying.map { cfg =>
    new IpAddressConfiguration(new SimpleAddressConfig(cfg))
  }

  override protected def validateConfig() {
    get
  }
}


