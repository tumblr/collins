package collins.models.shared

import java.sql.SQLException

import org.squeryl.PrimitiveTypeMode._
import org.squeryl.Schema

import play.api.Logger

import collins.callbacks.CallbackDatum
import collins.models.Asset
import collins.models.IpAddressKeys
import collins.models.cache.Cache
import collins.util.IpAddress
import collins.util.IpAddressCalc

trait IpAddressable extends ValidatedEntity[Long] with CallbackDatum {

  val assetId: Long
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
  def getAssetId(): Long = assetId
  def getAsset(): Asset = Asset.findById(getAssetId()).get

}

trait IpAddressStorage[T <: IpAddressable] extends Schema with AnormAdapter[T] with IpAddressKeys[T] { self: Schema with Keys[T] =>

  // name of ipaddress storage, ipmi etc
  def storageName: String

  // abstract
  protected def getConfig()(implicit scope: Option[String]): Option[AddressPool]

  protected[this] val logger = Logger.logger

  override def delete(a: T): Int = inTransaction {
    afterDeleteCallback(a) {
      tableDef.deleteWhere(i => i.id === a.id)
    }
  }

  def deleteByAsset(a: Asset): Int = inTransaction {
    findAllByAsset(a).foldLeft(0) {
      case (sum, ipInfo) =>
        sum + delete(ipInfo)
    }
  }

  def findAllByAsset(asset: Asset, checkCache: Boolean = true): List[T] = {
    lazy val op = inTransaction {
      tableDef.where(a => a.assetId === asset.id).toList
    }
    if (checkCache) {
      Cache.get(findAllByAssetKey(asset.id), op)
    } else {
      inTransaction {
        op
      }
    }
  }

  def findByAsset(asset: Asset): Option[T] = Cache.get(findByAssetKey(asset.id), inTransaction {
    tableDef.where(a => a.assetId === asset.id).headOption
  })

  def getNextAvailableAddress(overrideStart: Option[String] = None)(implicit scope: Option[String]): Tuple3[Long, Long, Long] = {
    //this is used by ip allocation without pools (i.e. IPMI)
    val network = getNetwork()(scope)
    val startAt = overrideStart.orElse(getStartAddress)
    val calc = IpAddressCalc(network, startAt)
    val gateway: Long = getGateway().getOrElse(calc.minAddressAsLong)
    val netmask: Long = calc.netmaskAsLong
    // look for the local maximum address (i.e. the last used address in a continuous sequence from startAddress)
    val localMax: Option[Long] = getCurrentLowestLocalMaxAddress(calc)
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
        case e: Throwable =>
          logger.warn("Uncaught exception %s".format(e.getMessage), e)
          throw e
      } finally {
        i += 1
      }
    } while (!res.isDefined && i < retryCount)
    res.getOrElse(
      throw new RuntimeException("Unable to create address after %d tries".format(retryCount)))
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
  protected def getCurrentLowestLocalMaxAddress(calc: IpAddressCalc)(implicit scope: Option[String]): Option[Long] = inTransaction {
    val startAddress = calc.startAddressAsLong
    val maxAddress = calc.maxAddressAsLong
    val sortedAddresses = from(tableDef)(t =>
      where(
        (t.address gte startAddress) and
          (t.address lte maxAddress))
        select (t.address)
        orderBy (t.address asc)).toSeq

    lazy val localMaximaAddresses = for {
      i <- Range(0, sortedAddresses.size - 1).inclusive.toStream
      curr = sortedAddresses(i)
      next = sortedAddresses.lift(i + 1)
      nextAddress = calc.incrementAddressUnchecked(curr)
      // address should not have an allocated address logically after it
      if (next.map { _ > nextAddress }.getOrElse(true))
      // address should not be the last address in the IP range
      if (curr < maxAddress)
    } yield curr

    localMaximaAddresses.headOption
  }

  protected def getGateway()(implicit scope: Option[String]): Option[Long] = getConfig() match {
    case None => None
    case Some(config) => config.gateway match {
      case Some(value) => Option(IpAddress.toLong(value))
      case None        => None
    }
  }
  protected def getNetwork()(implicit scope: Option[String]): String = getConfig() match {
    case None         => throw new RuntimeException("no %s configuration found".format(getClass.getName))
    case Some(config) => config.network
  }
  protected def getStartAddress()(implicit scope: Option[String]): Option[String] = getConfig() match {
    case None    => None
    case Some(c) => c.startAddress
  }

}
