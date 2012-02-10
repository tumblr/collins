package util

import org.specs2.mutable._
import org.specs2.matcher.DataTables

class IpAddressSpec extends Specification with DataTables {

  "IpAddress conversions" should {

    "support converting string addresses to long" >> {
      "address"         || "long"      |
      "170.112.108.147" !! 2859494547L |
      "10.60.25.33"     !! 171710753L  |
      "10.0.0.1"        !! 167772161L  |
      "255.255.224.0"   !! 4294959104L |
      "255.255.255.255" !! 4294967295L |> {
      (address,long) =>
        IpAddress.toLong(address) mustEqual(long)
      }
    }

    "support converting long addresses to strings" >> {
      "long"      | "address"         |
      2859494547L ! "170.112.108.147" |
      167772161L  ! "10.0.0.1"        |
      4294959104L ! "255.255.224.0"   |
      171710753L  ! "10.60.25.33"     |
      4294967295L ! "255.255.255.255" |> {
      (long,address) =>
        IpAddress.toString(long) mustEqual(address)
      }
    }

    "handle basic nextAvailableAddress functionality" >> {
      "initial address" || "expected next address" |
      "10.0.0.1"        !! "10.0.0.2"              |
      "10.0.0.254"      !! "10.0.1.2"              |> {
      (initial,expected) =>
        val initialAsLong = IpAddress.toLong(initial)
        val next = IpAddress.nextAvailableAddress(initialAsLong, 0)
        next mustEqual(IpAddress.toLong(expected))
      }
    }

  } // IpAddress conversions should

}
