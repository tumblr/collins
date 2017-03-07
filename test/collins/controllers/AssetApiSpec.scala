package collins.controllers

import org.specs2.mutable

import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.api.test.WithApplication

import collins.FakeRequest
import collins.ResourceFinder
import collins.models.AssetMeta

class AssetApiSpec extends mutable.Specification with ControllerSpec with ResourceFinder {

  "Asset API Specification".title

  args(sequential = true)

  "Create a meta" should {
    "for outlets" in new WithApplication(FakeApplication(
      additionalConfiguration = Map(
        "solr.enabled" -> false))) {
      AssetMeta.findOrCreateFromName("OUTLET").name === "OUTLET"
    }
  }

  "The REST API" should {

    "During Asset Validation" in {
      "Reject empty asset tags" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "B0001"
        val request = FakeRequest("GET", "/api/asset/")
        Extract.from(api.createAsset("").apply(request)) must haveStatus(400)
      }

      "Reject Non alpha-num tags" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "B0001"
        val request = FakeRequest("GET", "/api/asset/")
        Extract.from(api.updateAsset("^*$lkas$").apply(request)) must haveStatus(400)
      }
    }

    "Support Multi-step intake" in {
      "Create an asset via a PUT" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "A0001"
        val result = createAsset()
        result must haveStatus(201)
        result must haveJsonData.which { s =>
          s must /("data") */ ("ASSET") / ("STATUS" -> "Incomplete")
          s must /("data") */ ("IPMI") / ("IPMI_GATEWAY" -> "172.16.32.1")
          s must /("data") */ ("IPMI") / ("IPMI_NETMASK" -> "255.255.240.0")
        }
      }
      "Getting an asset" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "A0002"
        createAsset() must haveStatus(201)
        val req2 = FakeRequest("GET", assetUrl)
        val result2 = Extract.from(api.getAsset(assetTag).apply(req2))
        result2 must haveStatus(200)
      }
      "Inventory hardware information via a POST" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "A0003"
        createAsset() must haveStatus(201)
        val result = updateHwInfo()
        result must haveStatus(200)

        result must haveJsonData.which { s =>
          s must /("data") */ ("SUCCESS" -> true)
        }
        getAsset() must haveJsonData.which { txt =>
          txt must /("data") */ ("ATTRIBS") */ ("0") */ ("CHASSIS_TAG" -> "abbacadabra")
          Json.parse(txt) \ "data" \ "ASSET" \ "STATUS" mustEqual JsString("New")
        }
      }
      "Update the status after getting rack position and such" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "A0004"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)

        // update attributes
        val result = updateAttributes()

        result must haveJsonData.which { txt =>
          txt must /("data") */ ("SUCCESS" -> true)
        }
        val amf = AssetMeta.findByName("FOO")
        getAsset() must haveJsonData.which { txt =>
          Json.parse(txt) \ "data" \ "ASSET" \ "STATUS" mustEqual JsString("Unallocated")
          txt must /("data") */ ("POWER") */ ("UNITS") */ ("TYPE" -> "POWER_PORT")
          txt must /("data") */ ("POWER") */ ("UNITS") */ ("VALUE" -> "PORT 0")
          txt must /("data") */ ("POWER") */ ("UNITS") */ ("TYPE" -> "POWER_OUTLET")
          txt must /("data") */ ("POWER") */ ("UNITS") */ ("VALUE" -> "OUTLET 0")
          txt must /("data") */ ("ATTRIBS") */ ("0") */ ("RACK_POSITION" -> "rack 1")
          txt must /("data") */ ("ATTRIBS") */ ("0") */ ("FOO" -> "bar")
          txt must /("data") */ ("ATTRIBS") */ ("0") */ ("FIZZ" -> "buzz")
        }
      }
    } // Support multi-step intake
  } // The REST API
}
