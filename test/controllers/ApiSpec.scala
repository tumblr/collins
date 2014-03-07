package controllers

import models._
import util._

import org.specs2._
import specification._

import play.api.libs.json._
import play.api.libs.Files._
import play.api.mvc._
import play.api.mvc.MultipartFormData._

import test._

class ApiSpec extends ApplicationSpecification with ControllerSpec {

  args(sequential = true)
  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The API" should {
    "Handle Content Type's correctly" >> {
      "Based on Accept header" >> {
        def getExtractedResponse(accept: String) = {
          val req = FakeRequestHeader.withAcceptHeader(accept).asRequest()
          Extract.from(api.ping.apply(req))
        }
        "Bash" >> {
          val res = getExtractedResponse(BashOutput().contentType)
          res must provideBashResponse
        }
        "JSON" >> {
          val res = getExtractedResponse(JsonOutput().contentType)
          res must provideJsonResponse
        }
        "Text" >> {
          val res = getExtractedResponse(TextOutput().contentType)
          res must provideTextResponse
        }
      }
      "Based on URL" >> {
        def getExtractedResponse(path_ext: String) = {
          val req = FakeRequestHeader("GET", "/api/ping%s".format(path_ext)).asRequest()
          Extract.from(api.ping.apply(req))
        }
        "Bash" >> {
          val res = getExtractedResponse(BashOutput().fileExtension)
          res must provideBashResponse
        }
        "JSON" >> {
          val res = getExtractedResponse(JsonOutput().fileExtension)
          res must provideJsonResponse
        }
        "Text" >> {
          val res = getExtractedResponse(TextOutput().fileExtension)
          res must provideTextResponse
        }
      }
      "Based on Query String Parameter" >> {
        def getExtractedResponse(qsValue: String) = {
          val req = FakeRequestHeader("GET", "/api/ping?outputType=%s".format(qsValue)).asRequest()
          Extract.from(api.ping.apply(req))
        }
        "Bash" >> {
          val res = getExtractedResponse(BashOutput().queryString._2)
          res must provideBashResponse
        }
        "JSON" >> {
          val res = getExtractedResponse(JsonOutput().queryString._2)
          res must provideJsonResponse
        }
        "Text" >> {
          val res = getExtractedResponse(TextOutput().queryString._2)
          res must provideTextResponse
        }
      }
    } // Handle Content Type's correctly
  }

  def provideBashResponse = new ResponseMatcher(BashOutput().contentType) {
    override def expectedStatusCode = 200
    override def responseMatches(txt: String): Boolean = {
      txt.contains("""Data_TestList_0_name="foo123";""") &&
      txt.contains("""Data_TestList_0_key_with_dash="val-with-dash";""") && 
      txt.contains("""Status="Ok";""");
    }
  }

  def provideJsonResponse = new ResponseMatcher(JsonOutput().contentType) {
    override def expectedStatusCode = 200
    override def responseMatches(txt: String): Boolean = {
      val json = Json.parse(txt)
      val jsData = json \ "data"
      (jsData \ "Status").isInstanceOf[JsString] &&
      (jsData \ "Status").as[String].equals("Ok") &&
      (jsData \ "Data").isInstanceOf[JsObject] &&
      (jsData \ "Data" \ "TestList").isInstanceOf[JsArray] &&
      (jsData \ "Data" \ "TestList")(0).isInstanceOf[JsObject] &&
      ((jsData \ "Data" \ "TestList")(0) \ "id").as[Long] == 123L &&
      ((jsData \ "Data" \ "TestList") (0) \ "key-with-dash").as[String] == "val-with-dash"
    }
  }

  def provideTextResponse = new ResponseMatcher(TextOutput().contentType) {
    override def expectedStatusCode = 200
    override def responseMatches(txt: String): Boolean = {
      txt.contains("Status\tOk")
    }
  }

}
