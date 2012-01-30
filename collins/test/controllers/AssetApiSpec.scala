package controllers

import models._
import util.Helpers
import test._

import play.api.libs.json._
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import play.api.mvc.MultipartFormData._

import org.specs2._
import specification._
import matcher.Matcher

class AssetApiSpec extends ApplicationSpecification with ControllerSpec {

  "Asset API Specification".title

  args(sequential = true)

  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "Asset Validation" should {
    "Reject empty asset tags" in new ResponseScope {
      val request = FakeRequest("GET", "/api/asset/")
      Extract.from(api.createAsset("").apply(request)) must haveStatus(400)
    }
    "Reject Non alpha-num tags" in new ResponseScope {
      val request = FakeRequest("GET", "/api/asset/")
      Extract.from(api.updateAsset("^*$lkas$").apply(request)) must haveStatus(400)
    }
  }

  "The REST API" should {

    "Support Multi-step intake" in {
      "Create an asset via a PUT" in new asset {
        val request = FakeRequest("PUT", assetUrl)
        val result = Extract.from(api.createAsset(assetTag).apply(request))
        result must haveStatus(201)
        result must haveJsonData.which { s =>
          s must /("data") */("ASSET")/("STATUS" -> "Incomplete")
          s must /("data") */("IPMI")/("IPMI_GATEWAY" -> "10.0.0.1")
        }
      }
      "Inventory hardware information via a POST" in new asset {
        val lshwData = getResource("lshw-basic.xml")
        val lldpData = getResource("lldpctl-two-nic.xml")
        val dummy = Seq[FilePart[TemporaryFile]]()
        val mdf = MultipartFormData(Map(
          "lshw" -> Seq(lshwData),
          "lldp" -> Seq(lldpData),
          "CHASSIS_TAG" -> Seq("abbacadabra")
        ), dummy, Nil, Nil)
        val body = AnyContentAsMultipartFormData(mdf)
        val request = FakeRequest("POST", assetUrl).copy(body = body)
        val result = Extract.from(api.updateAsset(assetTag).apply(request))
        result must haveStatus(200)
        result must haveJsonData.which { s =>
          s must /("data") */("SUCCESS" -> true)
        }
        getAsset() must haveJsonData.which { txt =>
          txt must /("data") */("ATTRIBS") */("0") */("CHASSIS_TAG" -> "abbacadabra")
          txt must /("data") */("ASSET")/("STATUS" -> "New")
        }
      }
      "Update the status after getting rack position and such" in new asset {
        import AssetMeta.Enum.RackPosition
        import Helpers.formatPowerPort
        val rp: String = RackPosition.toString
        val body = AnyContentAsUrlFormEncoded(Map(
          rp -> Seq("rack 1"),
          formatPowerPort("A") -> Seq("power 1"),
          formatPowerPort("B") -> Seq("power 2"),
          "attribute" -> Seq("foo;bar","fizz;buzz")
        ))
        val req = FakeRequest("POST", assetUrl).copy(body = body)
        val result = Extract.from(api.updateAsset(assetTag).apply(req))
        result must haveStatus(200)
        result must haveJsonData.which { txt =>
          txt must /("data") */("SUCCESS" -> true)
        }
        getAsset() must haveJsonData.which { txt =>
          txt must /("data") */("ASSET")/("STATUS" -> "Unallocated")
          txt must /("data") */("ATTRIBS") */("0") */("RACK_POSITION" -> "rack 1")
          txt must /("data") */("ATTRIBS") */("0") */("POWER_PORT" -> "power 1")
          txt must /("data") */("ATTRIBS") */("1") */("POWER_PORT" -> "power 2")
          txt must /("data") */("ATTRIBS") */("0") */("FOO" -> "bar")
          txt must /("data") */("ATTRIBS") */("0") */("FIZZ" -> "buzz")
        }
      }
    } // Support multi-step intake

    "Support find" in {
      "by custom attribute" in new asset {
        val req = FakeRequest("GET", findUrl + "?attribute=foo;bar")
        val result = Extract.from(api.getAssets(0, 10, "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?attribute=fizz;buzz")
        Extract.from(api.getAssets(0, 10, "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
      }
      "by type" in new asset {
        val req = FakeRequest("GET", findUrl + "?type=SERVER_NODE")
        val result = Extract.from(api.getAssets(0, 10, "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
      }
      "by status" in new asset {
        val req = FakeRequest("GET", findUrl + "?status=Unallocated")
        val result = Extract.from(api.getAssets(0, 10, "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
      }
      "by createdAfter" in new asset {
        val req = FakeRequest("GET", findUrl + "?createdAfter=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?createdAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
      "by createdBefore" in new asset {
        val req = FakeRequest("GET", findUrl + "?createdBefore=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?createdBefore=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
      "by updatedAfter" in new asset {
        val req = FakeRequest("GET", findUrl + "?updatedAfter=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?updatedAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
      "by updatedBefore" in new asset {
        val req = FakeRequest("GET", findUrl + "?updatedBefore=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?updatedAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
    } // Support various find mechanisms


    "Handle asset decommission" in {
      "For Unallocated assets" in new asset {
        val req = FakeRequest("DELETE", assetUrl)
        Extract.from(api.deleteAsset(assetTag).apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("SUCCESS" -> true)
        }
        getAsset() must haveJsonData.which { txt =>
          txt must /("data") */("ASSET")/("STATUS" -> "Decommissioned")
        }
      }
      "For Incomplete assets" in new ResponseScope {
        val req = FakeRequest("DELETE", "/api/asset/%s.json".format("tumblrtag1"))
        Extract.from(api.deleteAsset("tumblrtag1").apply(req)) must haveStatus(409)
      }
    }

  } // The REST API

  trait asset extends Scope with ResponseMatchHelpers {
    val assetTag = "testAsset123"
    val assetUrl = "/api/asset/%s.json".format(assetTag)
    val findUrl = "/api/assets.json"

    def getAsset() = {
      val request = FakeRequest("GET", assetUrl)
      Extract.from(api.getAsset(assetTag).apply(request))
    }
  }


}
