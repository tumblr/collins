package collins.controllers

import org.specs2.matcher.JsonMatchers
import org.specs2.mutable

import play.api.test.WithApplication

import collins.FakeRequest
import collins.ResponseMatchHelpers

class AssetTypeApiSpec extends mutable.Specification with ControllerSpec {

  "Asset Type API Specification".title

  args(sequential = true)

  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The REST API" should {

	"Asset Type Validation" in {
      "Reject empty asset tags" in new WithApplication with ResponseMatchHelpers {
        val request = FakeRequest("GET", "/api/assettype/")
        Extract.from(api.createAssetType("").apply(request)) must haveStatus(400)
      }
    }
    "Support creation" in {
      "via PUT" in new WithApplication with assetType {
        val request = FakeRequest("PUT", assetTypeUrl + "?label=testlabel")
        val result = Extract.from(api.createAssetType(aname).apply(request))
        result must haveStatus(201)
        result must haveJsonData.which { s =>
          s must /("data") */("SUCCESS" -> true)
        }
      }
      "error if no label given" in new WithApplication with assetType {
        val request = FakeRequest("PUT", assetTypeUrl)
        val result = Extract.from(api.createAssetType(aname).apply(request))
        result must haveStatus(400)
      }
    }

    "Support updating" in {
      "via POST" in new WithApplication with assetType {
        val createRequest = FakeRequest("PUT", assetTypeUrl + "?label=updatedlabel")
        Extract.from(api.createAssetType(aname).apply(createRequest))
        val request = FakeRequest("POST", assetTypeUrl + "?label=updatedlabel")
        val result = Extract.from(api.updateAssetType(aname).apply(request))
        result must haveStatus(200)
        result must haveJsonData.which { s =>
          s must /("data") */("SUCCESS" -> true)
        }
      }
    }

    "Support getting" in {
      "all asset types" in new WithApplication with assetType {
        val createRequest = FakeRequest("PUT", assetTypeUrl + "?label=updatedlabel")
        Extract.from(api.createAssetType(aname).apply(createRequest))
        val req2 = FakeRequest("GET", findUrl)
        val result2 = Extract.from(api.getAssetTypes.apply(req2))
        result2 must haveStatus(200)
        result2 must haveJsonData.which { txt =>
          txt must */("NAME" -> aname)
        }
      }
      "by name" in new WithApplication with assetType {
        val createRequest = FakeRequest("PUT", assetTypeUrl + "?label=updatedlabel")
        Extract.from(api.createAssetType(aname).apply(createRequest))
        val req2 = FakeRequest("GET", assetTypeUrl)
        val result2 = Extract.from(api.getAssetType(aname).apply(req2))
        result2 must haveStatus(200)
        result2 must haveJsonData.which { txt =>
          txt must /("data") */("NAME" -> aname)
          txt must /("data") */("LABEL" -> "updatedlabel")
        }
      }
    }


    "Support deleting" in {
      "by name" in new WithApplication with assetType {
        val createRequest = FakeRequest("PUT", assetTypeUrl + "?label=updatedlabel")
        Extract.from(api.createAssetType(aname).apply(createRequest))
        val req = FakeRequest("DELETE", assetTypeUrl)
        Extract.from(api.deleteAssetType(aname).apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("DELETED" -> 1)
        }
      }
    }

  }

  trait assetType extends ResponseMatchHelpers with JsonMatchers {
    val aname = "SERVICE"
    val assetTypeUrl = "/api/assettype/%s.json".format(aname)
    val findUrl = "/api/assettypes"

    def getAssetType() = {
      val request = FakeRequest("GET", assetTypeUrl)
      Extract.from(api.getAssetType(aname).apply(request))
    }
  }


}
