package util

import org.apache.commons.net.util.SubnetUtils

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Convert IpAddresses from dotted quad to long and long to dotted quad
 *
 * NOTE: This does not support IPv6 because MySQL BIGINT is only 8 bytes (not 16).
 *       The storage backend could be updated to include 2xBIGINT fields easily enough.
 */
object IpAddress {

  def toString(address: Long) = {
    val byteBuffer = ByteBuffer.allocate(8)
    val addressBytes = byteBuffer.putLong(address)
    // The below is needed because we don't have an unsigned Long, and passing a byte array
    // with more than 4 bytes causes InetAddress to interpret it as a (bad) IPv6 address
    val tmp = new Array[Byte](4)
    Array.copy(addressBytes.array, 4, tmp, 0, 4)
    InetAddress.getByAddress(tmp).getHostAddress()
  }

  def toOptLong(_address: String): Option[Long] = try {
    Some(IpAddress.toLong(_address))
  } catch {
    case e => None
  }

  def toLong(_address: String): Long = {
    val address = try {
      InetAddress.getByName(_address)
    } catch {
      case e => throw new IllegalArgumentException("Could not parse address: " + e.getMessage)
    }
    val addressBytes = address.getAddress
    val bb = ByteBuffer.allocate(8)
    addressBytes.length match {
      case 4 =>
        bb.put(Array[Byte](0,0,0,0)) // Need a filler
        bb.put(addressBytes)
      case n =>
        throw new IndexOutOfBoundsException("Expected 4 byte address, got " + n)
    }
    bb.getLong(0)
  }

  def padRight(address: String, fill: String) = {
    address.split('.').toList match {
      case first :: second :: third :: last :: Nil => address
      case first :: second :: third :: Nil =>
        "%s.%s.%s.%s".format(first, second, third, fill)
      case first :: second :: Nil =>
        "%s.%s.%s.%s".format(first, second, fill, fill)
      case first :: Nil =>
        "%s.%s.%s.%s".format(first, fill, fill, fill)
      case _ => "%s.%s.%s.%s".format(fill, fill, fill, fill)
    }
  }
  def netmaskFromPad(address: String, fill: String) = {
    address.split('.').filter(_ == fill).size match {
      case 4 => "0.0.0.0"
      case 3 => "255.0.0.0"
      case 2 => "255.255.0.0"
      case 1 => "255.255.255.0"
      case 0 => "255.255.255.255"
    }
  }

  def firstOctet(address: Long) = (address >> 24) & 0xff
  def secondOctet(address: Long) = (address >> 16) & 0xff
  def thirdtOctet(address: Long) = (address >> 8) & 0xff
  def lastOctet(address: Long) = address & 0xff

}

object IpAddressCalc {
  def apply(address: Long, netmask: Long, startAt: Option[String]) = {
    val subnet = new SubnetUtils(IpAddress.toString(address), IpAddress.toString(netmask))
    new IpAddressCalc(subnet.getInfo.getCidrSignature, startAt)
  }
  def apply(address: String, netmask: String, startAt: Option[String]) = {
    val subnet = new SubnetUtils(address, netmask)
    new IpAddressCalc(subnet.getInfo.getCidrSignature, startAt)
  }
}

case class IpAddressCalc(network: String, startAt: Option[String] = None) {
  val subnetInfo = new SubnetUtils(network).getInfo
  if (startAt.isDefined)
    require(
      subnetInfo.isInRange(startAt.get),
      "%s is not in network %s".format(startAt.get, network)
    )
  def addressCount: Int = subnetInfo.getAddressCount
  def broadcastAddress: String = subnetInfo.getBroadcastAddress
  def broadcastAddressAsLong = IpAddress.toLong(broadcastAddress)
  def lastOctetInRange: Long = IpAddress.lastOctet(maxAddressAsLong)
  def startAddress: String = startAt.getOrElse(minAddress)
  def startAddressAsLong = IpAddress.toLong(startAddress)
  def minAddress: String = subnetInfo.getLowAddress()
  def minAddressAsLong = IpAddress.toLong(minAddress)
  def maxAddress: String = subnetInfo.getHighAddress()
  def maxAddressAsLong = IpAddress.toLong(maxAddress)
  def netmask: String = subnetInfo.getNetmask
  def netmaskAsLong = IpAddress.toLong(netmask)
  def nextAvailable(currentMax: Option[Long] = None) =
    IpAddress.toString(nextAvailableAsLong(currentMax))
  def nextAvailableAsLong(currentMax: Option[Long] = None): Long = currentMax match {
    case Some(l) =>
      incrementAsLong(l)
    case None => startAt match {
      case Some(start) => IpAddress.toLong(start)
      case None =>
        minAddressAsLong + 1
    }
  }
  protected def increment(address: Long): String = {
    IpAddress.toString(incrementAsLong(address))
  }
  protected def incrementAsLong(address: Long): Long = {
    val nextAddress = IpAddress.lastOctet(address) match {
      // 4 = n + 1 is broadcast, n + 2 is network, n + 3 is first address
      case next if next >= lastOctetInRange => address + 3
      case byTwo if IpAddress.lastOctet(address) == 0 => address + 2
      case _ => address + 1
    }
    val nextAddressString = IpAddress.toString(nextAddress)
    try {
      subnetInfo.isInRange(nextAddressString) match {
        case true => nextAddress
        case false => throw new RuntimeException(
          "Next available address %s is not in network block %s".format(
            nextAddressString, network
          )
        )
      }
    } catch {
      case e => 
        throw new RuntimeException(
          "Next available address %s is not a valid address in %s".format(
            nextAddressString, network
          )
        )
    }
  }
}
