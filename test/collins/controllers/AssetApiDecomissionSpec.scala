package collins.controllers

import org.specs2.mutable

import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.api.test.WithApplication

import collins.models.Status
import collins.FakeRequest
import collins.ResourceFinder

class AssetApiDecomissionSpec extends mutable.Specification with ControllerSpec with ResourceFinder {

  "Asset API Decomission Specification".title

  args(sequential = true)

  "The REST API" should {
    "Handle asset decommission" in {
      "For Unallocated assets" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "C0001"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateStatus(Status.Decommissioned.get, "Validate decomissioned asset") must haveStatus(200)

        val req = FakeRequest("DELETE", assetUrl + "?reason=foo")
        Extract.from(api.deleteAsset(assetTag).apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("SUCCESS" -> true)
        }
        getAsset() must haveJsonData.which { txt =>
          Json.parse(txt) \ "data" \ "ASSET" \ "STATUS" mustEqual JsString("Decommissioned")
        }
      }
      "For Incomplete assets" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false))) with AssetApiHelper {
        override val assetTag = "C0002"
        createAsset() must haveStatus(201)
        val req = FakeRequest("DELETE", assetUrl + "?reason=foo")
        Extract.from(api.deleteAsset(assetTag).apply(req)) must haveStatus(409)
      }
    }
  }
}
