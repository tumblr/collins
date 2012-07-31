package models
package shared

import org.specs2._
import specification._
import org.specs2.matcher._

class AddressPoolSpec extends mutable.Specification {

  class AddressPoolScope(network: String, startAddress: Option[String]) extends Scope {
    val pool = AddressPool("TEST", network, startAddress, None)
    def useAddress(address: String) = pool.useAddress(address)
    def unuseAddress(address: String) = pool.unuseAddress(address)
    def nextAddress(): String = pool.nextDottedAddress()
    def useNext(n: Int) {
      for (i <- 0 until n) pool.useAddress(nextAddress)
    }
    def usedAddressCount(): Int = pool.usedDottedAddresses.size
  }

  val DEFAULT_NET = "172.16.16.0/24"

  "Address Pools" should {

    "Support a default start address" in new AddressPoolScope(DEFAULT_NET, None) {
      usedAddressCount === 0
      nextAddress === "172.16.16.1"
      useNext(10)
      usedAddressCount === 10
      nextAddress === "172.16.16.11"
      unuseAddress("172.16.16.5")
      usedAddressCount === 9
      nextAddress === "172.16.16.5"
    }
    "Support a specified start address" in new AddressPoolScope(DEFAULT_NET, Some("172.16.16.10")) {
      usedAddressCount === 0
      nextAddress === "172.16.16.10"
      useAddress(nextAddress)
      usedAddressCount === 1
      nextAddress === "172.16.16.11"
      unuseAddress("172.16.16.10")
      usedAddressCount === 0
      nextAddress === "172.16.16.10"
    }

    "Support sparse IP ranges" in new AddressPoolScope(DEFAULT_NET, Some("172.16.16.200")) {
      usedAddressCount === 0
      nextAddress === "172.16.16.200"
      useNext(20)
      usedAddressCount === 20
      nextAddress === "172.16.16.220"
      unuseAddress("172.16.16.215")
      usedAddressCount === 19
      nextAddress === "172.16.16.215"
    }

    "Support using out of range IP addresses" in new AddressPoolScope(DEFAULT_NET, Some("172.16.16.10")) {
      nextAddress === "172.16.16.10"
      useAddress("172.16.16.2")
      usedAddressCount === 1
      nextAddress === "172.16.16.10"
      useAddress(nextAddress)
      usedAddressCount === 2
      nextAddress === "172.16.16.11"
    }

    "Misbehave if over allocating" in new AddressPoolScope("172.16.16.0/29", Some("172.16.16.5")) {
      nextAddress === "172.16.16.5"
      useAddress(nextAddress)
      usedAddressCount === 1
      nextAddress === "172.16.16.6"
      useAddress(nextAddress)
      usedAddressCount === 2
      nextAddress must throwA[RuntimeException]
      useAddress("172.16.16.7") must throwA[RuntimeException]
      unuseAddress("10.10.10.10") must throwA[RuntimeException]
    }

  }

}
