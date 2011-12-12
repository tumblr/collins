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
}
