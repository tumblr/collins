package controllers

import util.power.PowerUnits
import models.AssetMeta.Enum.RackPosition
import models._
import test._
import play.api.libs.json._
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import play.api.mvc.MultipartFormData._
import org.specs2._
import specification._
import matcher.Matcher
import play.api.test.WithApplication

class AssetApiSpec extends mutable.Specification with ControllerSpec with ResourceFinder {

  "Asset API Specification".title

  args(sequential = true)

  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "Create a meta" should {
    "for outlets" in new WithApplication {
      AssetMeta.findOrCreateFromName("OUTLET").name === "OUTLET"
    }
  }


  "The REST API" should {
 
    "During Asset Validation" in new WithApplication {
      "Reject empty asset tags" in new ResponseScope  {
        val request = FakeRequest("GET", "/api/asset/")
        Extract.from(api.createAsset("").apply(request)) must haveStatus(400)
    }

     "Reject Non alpha-num tags" in new ResponseScope {
       val request = FakeRequest("GET", "/api/asset/")
       Extract.from(api.updateAsset("^*$lkas$").apply(request)) must haveStatus(400)
     }
    }

    "Support Multi-step intake" in new WithApplication {
      "Create an asset via a PUT" in new asset {
        val request = FakeRequest("PUT", assetUrl)
        val result = Extract.from(api.createAsset(assetTag).apply(request))
        result must haveStatus(201)
        result must haveJsonData.which { s =>
          s must /("data") */("ASSET")/("STATUS" -> "Incomplete")
          s must /("data") */("IPMI")/("IPMI_GATEWAY" -> "172.16.32.1")
          s must /("data") */("IPMI")/("IPMI_NETMASK" -> "255.255.240.0")
        }
      }
      "Getting an asset" in new asset {
        val req2 = FakeRequest("GET", assetUrl)
        val result2 = Extract.from(api.getAsset(assetTag).apply(req2))
        result2 must haveStatus(200)
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
        val request = FakeRequest("POST", assetUrl, body)
        val result = Extract.from(api.updateAsset(assetTag).apply(request))
        result must haveStatus(200)
        result must haveJsonData.which { s =>
          s must /("data") */("SUCCESS" -> true)
        }
        getAsset() must haveJsonData.which { txt =>
          txt must /("data") */("ATTRIBS") */("0") */("CHASSIS_TAG" -> "abbacadabra")
          Json.parse(txt) \ "data" \ "ASSET" \ "STATUS" mustEqual JsString("New")
        }
      }
      "Update the status after getting rack position and such" in new asset {
        val rp: String = RackPosition.toString
        val units = PowerUnits()
        val unitSeq = (for(unit <- units; component <- unit) yield
          (component.key, Seq("%s %d".format(component.componentType.name, component.id)))).toSeq
        val powerMap = Map(unitSeq:_*)
        val body = AnyContentAsFormUrlEncoded(Map(
          rp -> Seq("rack 1"),
          "attribute" -> Seq("foo;bar","fizz;buzz")
        ) ++ powerMap)
        val req = FakeRequest("POST", assetUrl, body)
        val result = Extract.from(api.updateAsset(assetTag).apply(req))
        result must haveStatus(200)
        result must haveJsonData.which { txt =>
          txt must /("data") */("SUCCESS" -> true)
        }
        val amf = AssetMeta.findByName("FOO")
        getAsset() must haveJsonData.which { txt =>
          Json.parse(txt) \ "data" \ "ASSET" \ "STATUS" mustEqual JsString("Unallocated")
          txt must /("data") */("POWER") */("UNITS") */("TYPE" -> "POWER_PORT")
          txt must /("data") */("POWER") */("UNITS") */("VALUE" -> "PORT 0")
          txt must /("data") */("POWER") */("UNITS") */("TYPE" -> "POWER_OUTLET")
          txt must /("data") */("POWER") */("UNITS") */("VALUE" -> "OUTLET 0")
          txt must /("data") */("ATTRIBS") */("0") */("RACK_POSITION" -> "rack 1")
          txt must /("data") */("ATTRIBS") */("0") */("FOO" -> "bar")
          txt must /("data") */("ATTRIBS") */("0") */("FIZZ" -> "buzz")
        }
      }
    } // Support multi-step intake

    /*
     * NOTE (DS) - If these tests suddenly start failing, try clearing the Solr index by shutting down solr and deleting the SOLR_HOME/data
     * directory
     */
    "Support find" in new WithApplication {
      "by custom attribute" in new asset {
        val req = FakeRequest("GET", findUrl + "?attribute=foo;bar")
        val result = Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?attribute=fizz;buzz")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
      }
      "by type" in new asset {
        val req = FakeRequest("GET", findUrl + "?type=SERVER_NODE")
        val result = Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
      }
      "by status" in new asset {
        val req = FakeRequest("GET", findUrl + "?status=Unallocated")
        val result = Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req))
        result must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
      }
      "by createdAfter" in new asset {
        val req = FakeRequest("GET", findUrl + "?createdAfter=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?createdAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
      "by createdBefore" in new asset {
        val req = FakeRequest("GET", findUrl + "?createdBefore=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?createdBefore=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
      "by updatedAfter" in new asset {
        val req = FakeRequest("GET", findUrl + "?updatedAfter=2011-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?updatedAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
      "by updatedBefore" in new asset {
        val req = FakeRequest("GET", findUrl + "?updatedBefore=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("Data") */("TAG" -> assetTag)
        }
        val req2 = FakeRequest("GET", findUrl + "?updatedAfter=2020-12-30T00:00:00")
        Extract.from(api.getAssets(0, 10, "", "TAG", "").apply(req2)) must haveJsonData.which { txt =>
          txt must /("data") */("Pagination") */("TotalResults" -> 0.0)
        }
      }
    } // Support various find mechanisms


    "Handle asset decommission" in new WithApplication {
      "For Unallocated assets" in new asset {
        val req = FakeRequest("DELETE", assetUrl)
        Extract.from(api.deleteAsset(assetTag).apply(req)) must haveJsonData.which { txt =>
          txt must /("data") */("SUCCESS" -> true)
        }
        getAsset() must haveJsonData.which { txt =>
          Json.parse(txt) \ "data" \ "ASSET" \ "STATUS" mustEqual JsString("Decommissioned")
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
