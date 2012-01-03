package controllers

import models._
import org.specs2.mutable._
import play.api.libs.json._
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import play.api.mvc.MultipartFormData._

import test._

class AssetApiSpec extends ApplicationSpecification with ControllerSpec {

  args(sequential = true)
  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The Asset API" should {
    "Reject intake with bad tag" >> {
      "Empty is rejected" >> {
        val request = FakeRequest("GET", "/api/asset/")
        val result = Extract.from(api.createAsset("").apply(request))
        result._1 mustEqual(400)
      }
      "Non alpha-num is rejected" >> {
        val request = FakeRequest("GET", "/api/asset/")
        val result = Extract.from(api.updateAsset("^*$lkas$").apply(request))
        result._1 mustEqual(400)
      }
    }
    "Support a multi-step intake" >> {
      val assetTag = "testAsset123"
      val assetUrl = "/api/asset/%s.json".format(assetTag)
      "Step 1 - Intake Started" >> {
        val request = FakeRequest("PUT", assetUrl)
        val result = Extract.from(api.createAsset(assetTag).apply(request))
        result._1 mustEqual(201)
        val jsonResponse = Json.parse(result._3)
        jsonResponse \ "data" \ "ASSET" must haveClass[JsObject]
        (jsonResponse \ "data" \ "ASSET" \ "STATUS").as[String] mustEqual("Incomplete")
        jsonResponse \ "data" \ "IPMI" must haveClass[JsObject]
      }
      "Step 2 - Intake Finished" >> {
        val lshwData = getResource("lshw-basic.xml")
        val lldpData = getResource("lldpctl-two-nic.xml")
        val dummy = Seq[FilePart[TemporaryFile]]()
        val mdf = MultipartFormData(Map(
          "LSHW" -> Seq(lshwData),
          "LLDP" -> Seq(lldpData),
          "CHASSIS_TAG" -> Seq("abbacadabra")
        ), dummy, Nil, Nil)
        val body = AnyContentAsMultipartFormData(mdf)
        val request = FakeRequest("POST", assetUrl).copy(body = body)
        val result = Extract.from(api.updateAsset(assetTag).apply(request))
        result._1 mustEqual(200)
        val jsonResponse = Json.parse(result._3)
        jsonResponse \ "data" \ "SUCCESS" must haveClass[JsBoolean]
        (jsonResponse \ "data" \ "SUCCESS").as[Boolean] must beTrue
      }
      "Step 3 - Review Intake Data" >> {
        val req = FakeRequest("GET", "/api/asset/%s.json".format(assetTag))
        val res = Extract.from(api.getAsset(assetTag).apply(req))
        res._1 mustEqual(200)
        val jsonResponse = Json.parse(res._3)
        jsonResponse \ "data" \ "ASSET" must haveClass[JsObject]
        (jsonResponse \ "data" \ "ASSET" \ "STATUS").as[String] mustEqual "New"
      }
    }
  } // The Asset API

}
