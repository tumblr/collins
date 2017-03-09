package collins.controllers

import collins._
import org.specs2._
import specification._
import play.api.test.WithApplication
import org.specs2.matcher.JsonMatchers

class IpamApiSpec extends mutable.Specification with ControllerSpec {

  "IPAM API Specification".title

  args(sequential = true)

  "the REST API" should {
    "Support getting pools" in new WithApplication with AssetApiHelper {
      override val assetTag = "tumblrtag42"
      val getRequest = FakeRequest("GET", "/api/address/pools")
      val getResult = Extract.from(api.getAddressPools("true").apply(getRequest))
      getResult must haveStatus(200)
      getResult must haveJsonData.which { s =>
        s must /("data") */("POOLS") */ ("NAME" -> "ADMIN-OPS")
        s must /("data") */("POOLS") */ ("GATEWAY" -> "172.16.56.1")
        s must /("data") */("POOLS") */ ("START_ADDRESS" -> "172.16.56.5")
        s must /("data") */("POOLS") */ ("BROADCAST" -> "172.16.56.255")
        s must /("data") */("POOLS") */ ("POSSIBLE_ADDRESSES" -> 254)
     }
    }
    "Support creating an address starting with start address" in new WithApplication with AssetApiHelper {
      override val assetTag = "tumblrtag42"
      val pool = "ADMIN-OPS"
      createAsset() must haveStatus(201)
      val createRequest = FakeRequest("PUT", "/api/asset/%s/address?pool=%s".format(assetTag, pool))
      val createResult = Extract.from(api.allocateAddress(assetTag).apply(createRequest))
      createResult must haveStatus(201)
      createResult must haveJsonData.which { s =>
        s must /("data") */("ADDRESSES") */ ("ASSET_TAG" -> assetTag)
        s must /("data") */("ADDRESSES") */ ("ADDRESS" -> "172.16.56.5")
        s must /("data") */("ADDRESSES") */ ("GATEWAY" -> "172.16.56.1")
        s must /("data") */("ADDRESSES") */ ("NETMASK" -> "255.255.255.0")
        s must /("data") */("ADDRESSES") */ ("POOL" -> pool)
      }
    }
  }
}
