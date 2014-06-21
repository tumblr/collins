package models

import shared.AddressPool

import util.{IpAddress, IpAddressCalc}
import org.squeryl.Schema
import play.api.Logger
import java.sql.SQLException
import collection.immutable.SortedSet

trait IpAddressable extends ValidatedEntity[Long] {

  val asset_id: Long
  val gateway: Long
  val address: Long
  val netmask: Long
  val id: Long

  override def validate() {
    List(gateway, address, netmask).foreach { i =>
      require(i > 0, "IP gateway, address and netmask must be positive")
    }
  }

  def dottedAddress(): String = IpAddress.toString(address)
  def dottedGateway(): String = IpAddress.toString(gateway)
  def dottedNetmask(): String = IpAddress.toString(netmask)
  def getId(): Long = id
  def getAssetId(): Long = asset_id
  def getAsset(): Asset = Asset.findById(getAssetId()).get

}

trait IpAddressStorage[T <: IpAddressable] extends Schema with AnormAdapter[T] {
  import org.squeryl.PrimitiveTypeMode._

  lazy val className: String = getClass.getName.toString
  lazy val findAllByAssetKey: String = "%s.findAllByAsset(".format(className) + "%d)"
  lazy val findByAssetKey: String = "%s.findByAsset(".format(className) + "%d)"
  lazy val getKey: String = "%s.get(".format(className) + "%d)"

  // abstract
  protected def getConfig()(implicit scope: Option[String]): Option[AddressPool]

  override def cacheKeys(a: T) = Seq(
    findByAssetKey.format(a.asset_id),
    findAllByAssetKey.format(a.asset_id),
    getKey.format(a.id)
  )

  protected[this] val logger = Logger.logger

  override def delete(a: T): Int = inTransaction {
    afterDeleteCallback(a) {
      tableDef.deleteWhere(i => i.id === a.id)
    }
  }

  def deleteByAsset(a: Asset)(implicit mf: Manifest[T]): Int = inTransaction {
    findAllByAsset(a).foldLeft(0) { case(sum, ipInfo) =>
      sum + delete(ipInfo)
    }
  }

  def findAllByAsset(asset: Asset, checkCache: Boolean = true)(implicit mf: Manifest[T]): Seq[T] = {
    lazy val op = tableDef.where(a => a.asset_id === asset.getId).toList
    if (checkCache) {
      getOrElseUpdate(findAllByAssetKey.format(asset.getId)) (op)
    } else inTransaction {op}
  }

  def findByAsset(asset: Asset)(implicit mf: Manifest[T]): Option[T] = {
    getOrElseUpdate(findByAssetKey.format(asset.getId)) {
      tableDef.where(a => a.asset_id === asset.getId).headOption
    }
  }

  def getNextAvailableAddress(overrideStart: Option[String] = None)(implicit scope: Option[String]): Tuple3[Long,Long,Long] = {
    //this is used by ip allocation without pools (i.e. IPMI)
    val network = getNetwork
    val startAt = overrideStart.orElse(getStartAddress)
    val calc = IpAddressCalc(network, startAt)
    val gateway: Long = getGateway().getOrElse(calc.minAddressAsLong)
    val netmask: Long = calc.netmaskAsLong
    // look for the local maximum address (i.e. the last used address in a continuous sequence from startAddress)
    val localMax: Option[Long] = getCurrentLowestLocalMaxAddress(calc.minAddressAsLong, calc.maxAddressAsLong)
    val address: Long = calc.nextAvailableAsLong(localMax)
    (gateway, address, netmask)
  }

  // This is needed because if two clients both cause getNextAvailableAddress at the same time, they
  // both have the same value from getCurrentMaxAddress. One of them will successfully insert while
  // the other will fail. This allows a few retries before giving up.
  protected def createWithRetry(retryCount: Int)(f: Int => T): T = {
    var res: Option[T] = None
    var i = 0
    do {
      try {
        res = Some(f(i))
      } catch {
        case e: SQLException =>
          logger.info("createAddressWithRetry attempt %d: %s".format((i + 1), e.getMessage))
          res = None
        case e: RuntimeException =>
          logger.info("createAddressWithRetry attempt %d: %s".format((i + 1), e.getMessage))
          res = None
        case e =>
          logger.warn("Uncaught exception %s".format(e.getMessage), e)
          throw e
      } finally {
        i += 1
      }
    } while (!res.isDefined && i < retryCount)
    res.getOrElse(
      throw new RuntimeException("Unable to create address after %d tries".format(retryCount))
    )
  }

  /*
  * returns the lowest last used address in a continuous sequence from minAddress
  * If maxAddress is the same as the lowest last used address, we return None which signifies
  * that you should start allocating addresses from the start of the range.
  *
  * Ex: For a range 0L..20L, used addresses List(1,2,3,4,5,13,14,15,19,20), the result will be Some(5)
  * For a range 0L..20L, used addresses List(5,6,7,8,19,20), the result will be Some(8)
  * For a range 0L..20L, used addresses List(17,18,19,20), the result will be None (allocate from beginning)
  */
  protected def getCurrentLowestLocalMaxAddress(minAddress: Long, maxAddress: Long)(implicit scope: Option[String]): Option[Long] = {
    val addresses = from(tableDef)(t =>
      where(
        (t.address gte minAddress) and
        (t.address lte maxAddress)
      )
      select(t.address)
      orderBy(t.address asc)
    ).toSet

    val sortedAddresses = SortedSet[Long]() ++ addresses
    val localMaximaAddresses = for (
      localMax <- sortedAddresses if !sortedAddresses.contains(localMax + 1L)
    ) yield localMax

    localMaximaAddresses.headOption match {
      case Some(localMax) =>
        if (localMax == maxAddress) {
          //if we are at the end of our range, start from the beginning
          None
        } else {
          Some(localMax)
        }
      case _ => None
    }
  }

  protected def getGateway()(implicit scope: Option[String]): Option[Long] = getConfig() match {
    case None => None
    case Some(config) => config.gateway match {
      case Some(value) => Option(IpAddress.toLong(value))
      case None => None
    }
  }
  protected def getNetwork()(implicit scope: Option[String]): String = getConfig() match {
    case None => throw new RuntimeException("no %s configuration found".format(className))
    case Some(config) => config.network
  }
  protected def getStartAddress()(implicit scope: Option[String]): Option[String] = getConfig() match {
    case None => None
    case Some(c) => c.startAddress
  }

}
