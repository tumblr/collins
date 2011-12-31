package controllers

import models._
import util._

import org.specs2.mutable._
import org.specs2.matcher._
import play.api.libs.json._
import play.api.libs.Files._
import play.api.mvc._
import play.api.mvc.MultipartFormData._

import org.specs2.mock._

class ApiSpec extends models.DatabaseSpec with SpecHelpers {

  args(sequential = true)
  val user = getLoggedInUser("infra")
  val api = getApi(user)

  "The API" should {
    "Handle Content Type's correctly" >> {
      "Based on Accept header" >> {
        def getExtractedResponse(accept: String) = {
          val mockReq = MockRequest(headers = Seq(accept))
          val req = getRequest(mockReq)
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
          val mockReq = MockRequest(path = "/api/ping%s".format(path_ext))
          val req = getRequest(mockReq)
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
          val mockReq = MockRequest(queryString = Map("outputType" -> Seq(qsValue)))
          val req = getRequest(mockReq)
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

  type ResultTuple = Tuple3[Int, Map[String,String], String]
  val provideBashResponse = new Matcher[ResultTuple] {
    def apply[S <: ResultTuple](s: Expectable[S]) = {
      val code = s.value._1
      val headers = s.value._2
      val response = s.value._3
      val status: Boolean = code == 200 &&
        headers.contains("Content-Type") &&
        headers("Content-Type").contains(BashOutput().contentType) &&
        response.contains("""Data_TestList_0_name="foo123";""") &&
        response.contains("""Status="Ok";""");
      result(status, "Got expected Bash response", ("Invalid Bash response for " + s.value), s)
    }
  }

  val provideJsonResponse = new Matcher[ResultTuple] {
    def apply[S <: ResultTuple](s: Expectable[S]) = {
      val code = s.value._1
      val headers = s.value._2
      val response = s.value._3
      val json = Json.parse(response)
      val jsData = json \ "data"
      val status: Boolean = code == 200 &&
        headers.contains("Content-Type") &&
        headers("Content-Type").contains(JsonOutput().contentType) &&
        (jsData \ "Status").isInstanceOf[JsString] &&
        (jsData \ "Status").as[String].equals("Ok") &&
        (jsData \ "Data").isInstanceOf[JsObject] &&
        (jsData \ "Data" \ "TestList").isInstanceOf[JsArray] &&
        (jsData \ "Data" \ "TestList")(0).isInstanceOf[JsObject] &&
        ((jsData \ "Data" \ "TestList")(0) \ "id").as[Long] == 123L;
      result(status, "Got expected JSON response", ("Invalid JSON response for " + s.value), s)
    }
  }

  val provideTextResponse = new Matcher[ResultTuple] {
    def apply[S <: ResultTuple](s: Expectable[S]) = {
      val code = s.value._1
      val headers = s.value._2
      val response = s.value._3
      val status: Boolean = code == 200 &&
        headers.contains("Content-Type") &&
        headers("Content-Type").contains(TextOutput().contentType) &&
        response.contains("Status\tOk");
      result(status, "Got expected Text response", ("Invalid text response for " + s.value), s)
    }
  }


}
