package collins.controllers

import org.specs2.mutable

import play.api.libs.json.JsString
import play.api.libs.json.Json
import play.api.test.FakeApplication
import play.api.test.WithApplication
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.mvc.AnyContentAsMultipartFormData

import collins.models.Status
import collins.FakeRequest
import collins.ResourceFinder

class AssetApiProvisionSpec extends mutable.Specification with ControllerSpec with ResourceFinder {

  "Asset API Provision Specification".title

  args(sequential = true)

  "The REST API" should {
    "Handle asset provisioning" in {
      "Set specified attributes" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false,
          "provisioner.rate" -> "10/10 seconds",
          "privisioner.command" -> """printf "PC"""",
          "privisioner.checkCommand" -> """printf "PCC""""))) with AssetApiHelper {
        override val assetTag = "C0001"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateStatus(Status.Unallocated.get, "Validate provisioning of asset") must haveStatus(200)

        val req = FakeRequest("GET", assetUrl + "?profile=searchnode&contact=sre&suffix=aaa&primary_role=SEARCH&pool=SEARCH_POOL&secondary_role=MASTER")
        Extract.from(api.provisionAsset(assetTag).apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("SUCCESS" -> true)
        }

        getAsset() must haveJsonData.which { txt =>
          val json = Json.parse(txt)
          json \ "data" \ "ATTRIBS" \ "0" \ "PRIMARY_ROLE" mustEqual JsString("SEARCH")
          json \ "data" \ "ATTRIBS" \ "0" \ "NODECLASS" mustEqual JsString("searchnode")
          json \ "data" \ "ATTRIBS" \ "0" \ "POOL" mustEqual JsString("SEARCH_POOL")
          json \ "data" \ "ATTRIBS" \ "0" \ "SECONDARY_ROLE" mustEqual JsString("MASTER")
          json \ "data" \ "ATTRIBS" \ "0" \ "BUILD_CONTACT" mustEqual JsString("sre")
        }
      }

      "Only allow provisioning on valid status" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false,
          "provisioner.rate" -> "10/10 seconds",
          "privisioner.command" -> """printf "PC"""",
          "privisioner.checkCommand" -> """printf "PCC""""))) with AssetApiHelper {
        override val assetTag = "C0002"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateStatus(Status.New.get, "Validate provisioning of asset") must haveStatus(200)

        val req = FakeRequest("GET", assetUrl + "?profile=searchnode&contact=sre&suffix=aaa&primary_role=SEARCH&pool=SEARCH_POOL&secondary_role=MASTER")
        Extract.from(api.provisionAsset(assetTag).apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("message" -> "Provisioning prevented by configuration. Asset does not have allowed status")
        }
      }

      "Require a profile" in new WithApplication(FakeApplication(
        additionalConfiguration = Map(
          "solr.enabled" -> false,
          "provisioner.rate" -> "10/10 seconds"))) with AssetApiHelper {
        override val assetTag = "C0002"
        createAsset() must haveStatus(201)
        updateHwInfo() must haveStatus(200)
        updateStatus(Status.New.get, "Validate provisioning of asset") must haveStatus(200)

        val req = FakeRequest("GET", assetUrl + "?contact=sre&suffix=aaa&primary_role=SEARCH&pool=SEARCH_POOL&secondary_role=MASTER")
        Extract.from(api.provisionAsset(assetTag).apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */ ("message" -> "Profile must be specified")
        }
      }
    }
  }
}