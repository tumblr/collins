package models
package shared

import com.typesafe.config.{Config => TypesafeConfig}
import util.{IpAddress, IpAddressCalc, MessageHelper}
import util.config.{Configurable, ConfigurationAccessor}
import util.concurrent.LockingBitSet

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
 *
 * FIXME - cache no longer valid if ranges, etc can change (val -> def)
 */
object IpAddressConfiguration extends MessageHelper("ip_address") with Configurable {

  val DefaultPoolName = "DEFAULT"

  import AddressPool.poolName

  override val namespace = "ipAddresses"
  override val referenceConfigFilename = "ipaddresses_reference.conf"

  // Default pool to use, if configured, hidden since we may end up with a nake config which will
  // still end up with the DefaultPoolName
  def defaultPoolName: Option[String] = getString("defaultPoolName").filter(_.nonEmpty).map(poolName(_).toString)
  def strict = getBoolean("strict", false)

  // PoolName -> AddressPool map, if pools are specified
  def pools: Map[String,AddressPool] = getObjectMap("pools").map { case(name, pool) =>
    val poolConfig = new PoolConfig(name, pool.toConfig)
    val addressPool = AddressPool.fromConfiguration(poolConfig, true, strict).get
    poolName(addressPool.name) -> addressPool
  }.toMap

  // The default address pool, either one from the pools map, or if no pools were specified, assume
  // a 'naked' config (e.g. network/etc hanging off the key)
  def defaultPool: Option[AddressPool] = defaultPoolName.map { pool =>
    pools.get(poolName(pool)).getOrElse(
      throw globalError(message("invalidDefaultPool", pool))
    )
  }

  def hasDefault: Boolean = defaultPool.isDefined
  def hasPool(pool: String): Boolean = pools.contains(poolName(pool))
  def pool(name: String): Option[AddressPool] = pools.get(poolName(name))
  def poolNames: Set[String] = pools.keySet

  override protected def validateConfig() {
    defaultPoolName
    strict
    pools
    defaultPool
    hasDefault
    poolNames
  }

}

class PoolConfig(key: String, cfg: TypesafeConfig) extends ConfigurationAccessor {

  implicit val requirement = util.config.ConfigValue.Optional

  override protected def underlying = Some(cfg)

  def name = getString("name").getOrElse(key)
  def startAddress = getString("startAddress")
  def network = getString("network")
  def gateway = getString("gateway")

  override protected def underlying_=(config: Option[TypesafeConfig]) {
  }
}

case class AddressPool(
  name: String, network: String, startAddress: Option[String], gateway: Option[String]
) {

  require(name.toUpperCase == name, "pool name must be all caps")
  require(network.nonEmpty, "network must be nonEmpty")

  if (startAddress.isDefined)
    try {
      IpAddress.toLong(startAddress.get)
    } catch {
      case e => throw new IllegalArgumentException("%s is not a valid IPv4 address".format(
        startAddress.get
      ))
    }

  val ipCalc = try {
    IpAddressCalc(network, startAddress)
  } catch {
    case e => throw new IllegalArgumentException("%s%s is not a valid network%s".format(
      network,
      startAddress.map(s => ":%s".format(s)).getOrElse(""),
      startAddress.map(_ => "/startAddress").getOrElse("")
    ))
  }

  // Represent an IP range as a bit vector, where every address takes up a bit. We calculate the
  // used addresses as VectorIndex + ipCalc.minAddressAsLong. We calculate the next available
  // address as the next unused index >= the start address, plus the minAddress
  private[this] val addressCache = LockingBitSet(ipCalc.addressCount)

  def isInRange(address: String): Boolean = ipCalc.subnetInfo.isInRange(address)
  def isInRange(address: Long): Boolean = isInRange(IpAddress.toString(address))

  def clearAddresses() { addressCache.forWrite(_.clear()) }
  def hasAddressCache(): Boolean = !addressCache.forRead(_.isEmpty)
  def useAddress(address: String) {
    useAddress(IpAddress.toLong(address))
  }
  def useAddress(address: Long) {
    requireInRange(address)
    addressCache.forWrite(_.set(addressIndex(address)))
  }
  def unuseAddress(address: String) {
    unuseAddress(IpAddress.toLong(address))
  }
  def unuseAddress(address: Long) {
    requireInRange(address)
    addressCache.forWrite(_.set(addressIndex(address), false))
  }
  def nextDottedAddress(): String = {
    IpAddress.toString(nextAddress)
  }
  def nextAddress(): Long = {
    val idx = addressIndex(ipCalc.startAddressAsLong)
    val next = addressCache.forRead(_.nextClearBit(idx))
    val nextAddress = next + ipCalc.minAddressAsLong
    requireInRange(nextAddress)
    nextAddress
  }
  // mostly for testing
  protected[shared] def usedDottedAddresses(): Set[String] = {
    (for (i <- addressCache.indexIterator)
      yield IpAddress.toString(addressFromIndex(i))).toSet
  }

  // Ensure as a set, address pools with the same name are seen as equal
  override def equals(o: Any) = o match {
    case that: AddressPool => that.name.equalsIgnoreCase(this.name)
    case _ => false
  }
  override def hashCode = name.toUpperCase.hashCode

  def isMagic: Boolean = network == AddressPool.MagicNetwork // valid dummy value

  protected def addressIndex(address: Long): Int = {
    math.abs((address - ipCalc.minAddressAsLong).toInt)
  }
  protected def addressFromIndex(index: Int): Long = {
    index + ipCalc.minAddressAsLong
  }
  protected def requireInRange(address: Long) {
    val addressString = IpAddress.toString(address)
    if (!isInRange(address))
      throw new RuntimeException("Address %s is not in network block %s".format(
        addressString, network
      ))
  }
}

object AddressPool extends MessageHelper("ip_address") {

  val MagicNetwork = "127.0.0.1/32"

  def poolName(pool: String) = pool.toUpperCase

  def fromConfiguration(
    cfg: PoolConfig, required: Boolean, strict: Boolean
  ): Option[AddressPool] = {
    val startAddress = cfg.startAddress
    val network = cfg.network
    val name = cfg.name
    val gw = cfg.gateway
    (startAddress.isDefined || network.isDefined || gw.isDefined) match {
      case true =>
        if (!network.isDefined)
          throw cfg.globalError(message("invalidConfig", cfg.name))
        val normName = name.toUpperCase
        Some(AddressPool(normName, network.get, startAddress, gw))
      case false =>
        if (required)
          throw cfg.globalError(message("missingConfig", name))
        else
          None
    }
  }

}
