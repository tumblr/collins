package controllers

import models._
import org.specs2.mutable._
import play.api.libs.Files._
import play.api.mvc._
import play.api.mvc.MultipartFormData._
import play.api.test.Extract

import org.specs2.mock._

class ApiSpec extends models.DatabaseSpec with SpecHelpers {

  args(sequential = true)
  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The API" should {
    "Handle asset intake" >> {
      "Reject intake with bad tag" >> {
        "Empty is rejected" >> {
          val request = getRequest(MockRequest(path = "/api/asset/"))
          val result = Extract.from(api.asset("").apply(request))
          result._1 mustEqual(400)
        }
        "Non alpha-num is rejected" >> {
          val request = getRequest(MockRequest(path = "/api/asset/"))
          val result = Extract.from(api.asset("^*$lasc$").apply(request))
          result._1 mustEqual(400)
        }
      }
      "Support a multi-step intake" >> {
        val assetId = "testAsset123"
        val assetUrl = "/api/asset/%s.sh".format(assetId)
        "Step 1 - Intake Started" >> {
          val request = getRequest(MockRequest(path = assetUrl))
          val result = Extract.from(api.asset(assetId).apply(request))
          result._1 mustEqual(201)
          result._3 must contain("ASSET_STATUS=Incomplete")
        }
        "Step 2 - Intake Finished" >> {
          val lshwData = getResource("lshw-basic.xml")
          val dummy = Seq[FilePart[(String, TemporaryFile)]]()
          val mdf = MultipartFormData(Map(
            "lshw" -> Seq(lshwData)
          ), dummy, Nil)
          val body = AnyContentAsMultipartFormData(mdf)
          val request = getRequest(MockRequest(path = assetUrl, body = body))
          val result = Extract.from(api.asset(assetId).apply(request))
          println(result)
          result._1 mustEqual(200)
        }
        "Step 3 - Review Intake Data" >> {
          val req = getRequest(MockRequest(path = "/api/asset/%s.sh".format(assetId)))
          val res = Extract.from(api.findAssetMetaValues(assetId).apply(req))
          println(res)
          res._1 mustEqual(200)
        }
      }
    }
  }

  def getResource(filename: String) = {
    val stream = getClass.getClassLoader.getResourceAsStream(filename)
    val tmp = io.Source.fromInputStream(stream)
    val str = tmp.mkString
    tmp.close()
    str
  }

}
