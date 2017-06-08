package collins.controllers

import collins._
import org.specs2._
import specification._
import play.api.test.WithApplication
import org.specs2.matcher.JsonMatchers

class IpmiApiSpec extends mutable.Specification with ControllerSpec {

  "IPMI API Specification".title

  args(sequential = true)

  "the REST API" should {
    "Support getting multiple ipmi pools" in new WithApplication with AssetApiHelper {
      override val assetTag = "tumblrtag42"
      val getRequest = FakeRequest("GET", "/api/ipmi/pools")
      val getResult = Extract.from(api.getIpmiAddressPools.apply(getRequest))
      getResult must haveStatus(200)
      getResult must haveJsonData.which { s =>
        s must /("data") */("POOLS") */ ("NAME" -> "OOB-POD01")
        s must /("data") */("POOLS") */ ("GATEWAY" -> "172.16.32.1")
        s must /("data") */("POOLS") */ ("START_ADDRESS" -> "172.16.32.20")
        s must /("data") */("POOLS") */ ("BROADCAST" -> "172.16.47.255")
        s must /("data") */("POOLS") */ ("POSSIBLE_ADDRESSES" -> 4094)

        s must /("data") */("POOLS") */ ("NAME" -> "OOB-POD02")
        s must /("data") */("POOLS") */ ("GATEWAY" -> "172.99.32.1")
        s must /("data") */("POOLS") */ ("START_ADDRESS" -> "172.99.32.20")
        s must /("data") */("POOLS") */ ("BROADCAST" -> "172.99.47.255")
        s must /("data") */("POOLS") */ ("POSSIBLE_ADDRESSES" -> 4094)
      }
    }
  }
}
