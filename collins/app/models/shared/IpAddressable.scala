package models

import shared.{AddressPool, IpAddressConfiguration}

import util.{IpAddress, IpAddressCalc}
import org.squeryl.Schema
import play.api.{Configuration, Logger}
import java.sql.SQLException

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

  def findAllByAsset(asset: Asset)(implicit mf: Manifest[T]): Seq[T] = {
    getOrElseUpdate(findAllByAssetKey.format(asset.getId)) {
      tableDef.where(a => a.asset_id === asset.getId).toList
    }
  }

  def findByAsset(asset: Asset)(implicit mf: Manifest[T]): Option[T] = {
    getOrElseUpdate(findByAssetKey.format(asset.getId)) {
      tableDef.where(a => a.asset_id === asset.getId).headOption
    }
  }

  def getNextAvailableAddress(overrideStart: Option[String] = None)(implicit scope: Option[String]): Tuple3[Long,Long,Long] = {
    val network = getNetwork
    val startAt = overrideStart.orElse(getStartAddress)
    val calc = IpAddressCalc(network, startAt)
    val gateway: Long = getGateway().getOrElse(calc.minAddressAsLong)
    val netmask: Long = calc.netmaskAsLong
    val currentMax: Option[Long] = getCurrentMaxAddress(
      calc.minAddressAsLong, calc.maxAddressAsLong
    )
    val address: Long = calc.nextAvailableAsLong(currentMax)
    (gateway, address, netmask)
  }

  // This is needed because if two clients both cause getNextAvailableAddress at the same time, they
  // both have the same value from getCurrentMaxAddress. One of them will successfully insert while
  // the other will fail. This allows a few retries before giving up.
  protected def createWithRetry(retryCount: Int)(f: => T): T = {
    (0 until retryCount).foreach { _ =>
      try {
        val res = f
        return res
      } catch {
        case e: SQLException =>
        case e => throw e
      }
    }
    throw new Exception("Unable to create address after %d tries".format(retryCount))
  }

  protected def getCurrentMaxAddress(minAddress: Long, maxAddress: Long): Option[Long] = inTransaction {
    from(tableDef)(t =>
      where(
        (t.address gte minAddress) and
        (t.address lte maxAddress)
      )
      compute(max(t.address))
    )
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
