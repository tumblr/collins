package util

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

  // FIXME: this is horribly naive and broken, it completely ignores the netmask
  def nextAvailableAddress(address: Long, netmask: Long): Long = {
    lastOctet(address) match {
      case next if next >= 254 => address + 4 // 4 = n.255, n+1.0, n+1.1, n+1.2
      case _ => address + 1
    }
  }

  def firstOctet(address: Long) = (address >> 24) & 0xff
  def secondOctet(address: Long) = (address >> 16) & 0xff
  def thirdtOctet(address: Long) = (address >> 8) & 0xff
  def lastOctet(address: Long) = address & 0xff

}
