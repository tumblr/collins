package collins.controllers

import collins._
import org.specs2._
import specification._
import play.api.test.WithApplication
import org.specs2.matcher.JsonMatchers

class IpamApiSpec extends mutable.Specification with ControllerSpec {

  "IPAM API Specification".title

  args(sequential = true)

  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "the REST API" should {
    "Support getting pools" in new WithApplication with ResponseMatchHelpers with JsonMatchers {
      val getRequest = FakeRequest("GET", "/api/address/pools")
      val result = Extract.from(api.getAddressPools("true").apply(getRequest))
      result must haveStatus(200)
      result must haveJsonData.which { s =>
        s must /("data") */("POOLS") */ ("NAME" -> "ADMIN-OPS")
        s must /("data") */("POOLS") */ ("GATEWAY" -> "172.16.56.1")
        s must /("data") */("POOLS") */ ("START_ADDRESS" -> "172.16.56.5")
        s must /("data") */("POOLS") */ ("BROADCAST" -> "172.16.56.255")
        s must /("data") */("POOLS") */ ("POSSIBLE_ADDRESSES" -> 254)
      }
    }
  }
}
