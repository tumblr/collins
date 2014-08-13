package models
package shared

import org.specs2._
import specification._
import org.specs2.matcher._

class AddressPoolSpec extends mutable.Specification {

  /*
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
  */

  val DEFAULT_NET = "172.16.16.0/24"

  "Address Pools" should {

    "Pool name must be all caps" in {
      val good = AddressPool("TEST",DEFAULT_NET,None,None)
      good.name === "TEST"
      AddressPool("lowercasepool",DEFAULT_NET,None,None) must throwA[IllegalArgumentException]
    }

    "Error when given bogus networks" in {
      AddressPool("BAD_NET1","",None,None) must throwA[IllegalArgumentException]
      AddressPool("BAD_NET1","not a network",None,None) must throwA[IllegalArgumentException]
      val p = AddressPool("GOOD_NET1","10.2.0.0/23",None,None)
      p.network === "10.2.0.0/23"
    }

    "Start address should be a valid address" in {
      val p = AddressPool("GOOD_START1","10.2.0.0/23",None,None)
      p.name === "GOOD_START1"
      AddressPool("BAD_START1","10.2.0.0/23",Some(""),None) must throwA[IllegalArgumentException]
      AddressPool("BAD_START1","10.2.0.0/23",Some("bad start address"),None) must throwA[IllegalArgumentException]
    }

    "Start address should be within network range" in {
      val p = AddressPool("IN_RANGE1","10.2.0.0/23",Some("10.2.0.5"),None)
      p.startAddress.get === "10.2.0.5"
      AddressPool("OUT_OF_RANGE1","10.2.0.0/23",Some("192.168.1.1"),None) must throwA[IllegalArgumentException]
    }

    "Support checking if address is in pool" in {
      val p = AddressPool("TEST2","10.2.0.0/24",Some("10.2.0.5"),None)
      p.isInRange("10.2.0.0") === false
      p.isInRange("10.2.0.1") === true
      p.isInRange("10.0.0.0") === false
      p.isInRange("10.2.0.254") === true
      p.isInRange("10.2.0.255") === false
      p.isInRange("not an address") must throwA[IllegalArgumentException]
      p.isInRange(util.IpAddress.toLong("10.2.0.12")) === true
      p.isInRange(3232235777L) === false  // 192.168.1.1
    }

    "Support comparing pools by name" in {
      val p1 = AddressPool("P1","10.2.0.0/23",Some("10.2.0.5"),None)
      val p2 = AddressPool("P1","192.168.1.0/24",None,None)
      val p3 = AddressPool("P3","192.168.1.0/24",None,None)
      p1.equals(p2) === true
      p1.equals(p3) === false
    }

    "Support comparing pools strictly" in {
      val p1 = AddressPool("P1","10.2.0.0/23",Some("10.2.0.5"),None)
      val p2 = AddressPool("P1","192.168.1.0/24",None,None)
      val p3 = AddressPool("P1","10.2.0.0/23",Some("10.2.0.5"),None)
      val p4 = AddressPool("P1","10.2.0.0/23",None,None)
      p1.strictEquals(p2) === false
      p1.strictEquals(p3) === true
      p1.strictEquals(p4) === false
    }

  }

}
