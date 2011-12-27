package controllers

import models._
import org.specs2.mutable._
import play.api.libs.json._
import play.api.libs.Files._
import play.api.mvc._
import play.api.mvc.MultipartFormData._
import play.api.test.Extract

import org.specs2.mock._

class AssetApiSpec extends models.DatabaseSpec with SpecHelpers {

  args(sequential = true)
  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The Asset API" should {
    "Reject intake with bad tag" >> {
      "Empty is rejected" >> {
        val request = getRequest(MockRequest(path = "/api/asset/"))
        val result = Extract.from(api.createAsset("").apply(request))
        result._1 mustEqual(400)
      }
      "Non alpha-num is rejected" >> {
        val request = getRequest(MockRequest(path = "/api/asset/"))
        val result = Extract.from(api.updateAsset("^*$lasc$").apply(request))
        result._1 mustEqual(400)
      }
    }
    "Support a multi-step intake" >> {
      val assetId = "testAsset123"
      val assetUrl = "/api/asset/%s.json".format(assetId)
      "Step 1 - Intake Started" >> {
        val request = getRequest(MockRequest(path = assetUrl, method = "PUT"))
        val result = Extract.from(api.createAsset(assetId).apply(request))
        result._1 mustEqual(201)
        val jsonResponse = parseJson(result._3)
        jsonResponse \ "ASSET" must haveClass[JsObject]
        (jsonResponse \ "ASSET" \ "STATUS").valueAs[String] mustEqual("Incomplete")
        jsonResponse \ "IPMI" must haveClass[JsObject]
      }
      "Step 2 - Intake Finished" >> {
        val lshwData = getResource("lshw-basic.xml")
        val dummy = Seq[FilePart[(String, TemporaryFile)]]()
        val mdf = MultipartFormData(Map(
          "lshw" -> Seq(lshwData),
          "chassis_tag" -> Seq("abbacadabra")
        ), dummy, Nil)
        val body = AnyContentAsMultipartFormData(mdf)
        val request = getRequest(MockRequest(path = assetUrl, body = body, method = "POST"))
        val result = Extract.from(api.updateAsset(assetId).apply(request))
        result._1 mustEqual(200)
        val jsonResponse = parseJson(result._3)
        jsonResponse \ "SUCCESS" must haveClass[JsBoolean]
        (jsonResponse \ "SUCCESS").valueAs[Boolean] must beTrue
      }
      "Step 3 - Review Intake Data" >> {
        val req = getRequest(MockRequest(path = "/api/asset/%s.json".format(assetId)))
        val res = Extract.from(api.getAsset(assetId).apply(req))
        res._1 mustEqual(200)
        val jsonResponse = parseJson(res._3)
        jsonResponse \ "ASSET" must haveClass[JsObject]
        (jsonResponse \ "ASSET" \ "STATUS").valueAs[String] mustEqual "New"
      }
    }
  } // The Asset API

}
