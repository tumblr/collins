package collins.controllers

import org.specs2.mutable

import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsFormUrlEncoded
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
        val request = FakeRequest("PUT", assetUrl)
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

    "Supports Node Classifier in API" in {
      "create a configuration asset and check that an asset matches" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "node_classy"
        override def assetUrl = "/api/asset/%s".format(assetTag)
        val req = FakeRequest("PUT", assetUrl + "?type=CONFIGURATION")
        val result = Extract.from(api.createAsset(assetTag).apply(req))

        result must haveStatus(201)
        result must haveJsonData.which { s =>
          s must /("data") */ ("ASSET") / ("TAG" -> "node_classy")
        }
        
        // make asset a nodeclass configuration
        val requestBody = AnyContentAsFormUrlEncoded(Map(
          "attribute" -> Seq("IS_NODECLASS;true")))
        val req_att = FakeRequest("POST", assetUrl, requestBody)
        val result_att = Extract.from(api.updateAsset(assetTag).apply(req_att))
 
        result_att must haveStatus(200)
        result_att must haveJsonData.which { s =>
          s must /("data") */ ("SUCCESS" -> true)
        }

        // create asset
        var node_asset_tag = "U01234"
        val req_node = FakeRequest("PUT", "/api/asset/" + node_asset_tag)
        val result_node = Extract.from(api.createAsset(node_asset_tag).apply(req_node))

        result_node must haveStatus(201)
        result_node must haveJsonData.which { s =>
          s must /("data") */ ("ASSET") / ("TAG" -> node_asset_tag)
        }

        // get all node attributes
        val req_node_att = FakeRequest("GET", "/api/asset/" + node_asset_tag)
        val result_node_att = Extract.from(api.getAsset(node_asset_tag).apply(req_node_att))

        result_node_att must haveStatus(200)
        result_node_att must haveJsonData.which { s =>
          s must /("data") */ ("CLASSIFICATION") / ("TAG" -> "node_classy")
          s must /("data") */ ("CLASSIFICATION") / ("STATUS" -> "Incomplete")
          s must /("data") */ ("CLASSIFICATION") / ("TYPE" -> "CONFIGURATION")
          s must /("data") */ ("CLASSIFICATION") / ("DELETED" -> null)
        }

      }
      "Make sure nil classification is returned when no configuration is defined" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {

        override val assetTag = "U01235"
        val req_node = FakeRequest("PUT", "/api/asset/" + assetTag)
        val result_node = Extract.from(api.createAsset(assetTag).apply(req_node))

        result_node must haveStatus(201)
        result_node must haveJsonData.which { s =>
          s must /("data") */ ("ASSET") / ("TAG" -> assetTag)
        }

        // get all node attributes
        val req_node_att = FakeRequest("GET", "/api/asset/" + assetTag)
        val result_node_att = Extract.from(api.getAsset(assetTag).apply(req_node_att))

        result_node_att must haveStatus(200)
        result_node_att must haveJsonData.which { s =>
          // I can't get this to work with the normal "s must" style of testing
          Json.parse(s) \ "data" \ "CLASSIFICATION" mustEqual play.api.libs.json.JsNull
        }
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
