package models
package shared

import util.{IpAddress, IpAddressCalc, MessageHelper}
import util.concurrent.LockingBitSet
import util.config.SimpleAddressConfig

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

  def fromConfig(
    cfg: SimpleAddressConfig, uname: String, required: Boolean, strict: Boolean
  ): Option[AddressPool] = {
    val startAddress = cfg.startAddress
    val network = cfg.network
    val name = cfg.name
    val gw = cfg.gateway
    (startAddress.isDefined || network.isDefined || gw.isDefined) match {
      case true =>
        if (strict && !name.isDefined)
          throw cfg.globalError(message("strictConfig"))
        if (!network.isDefined)
          throw cfg.globalError(message("invalidConfig", uname))
        val normName = name.getOrElse(uname).toUpperCase
        Some(AddressPool(normName, network.get, startAddress, gw))
      case false =>
        if (required)
          throw cfg.globalError(message("missingConfig", uname))
        else
          None
    }
  }

  def fromConfig(
    cfg: Option[SimpleAddressConfig], uname: String, required: Boolean, strict: Boolean
  ): Option[AddressPool] = cfg match {
    case None =>
      if (required)
        throw new IllegalArgumentException(messageWithDefault(
          "missingConfig", "no config defined", uname
        ))
      else
        None
    case Some(config) => fromConfig(config, uname, required, strict)
  }
}
