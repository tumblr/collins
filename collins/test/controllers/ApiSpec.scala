package controllers

import models._
import util._

import org.specs2.mutable._
import org.specs2.matcher._
import play.api.json._
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
    "Handle Content Type's correctly" >> {
      "Based on Accept header" >> {
        def getExtractedResponse(accept: String) = {
          val mockReq = MockRequest(headers = Seq(accept))
          val req = getRequest(mockReq)
          Extract.from(api.ping.apply(req))
        }
        "Bash" >> {
          val res = getExtractedResponse(BashOutput.acceptValue)
          res must provideBashResponse
        }
        "JSON" >> {
          val res = getExtractedResponse(JsonOutput.acceptValue)
          res must provideJsonResponse
        }
        "Text" >> {
          val res = getExtractedResponse(TextOutput.acceptValue)
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
          val res = getExtractedResponse(BashOutput.fileExtension)
          res must provideBashResponse
        }
        "JSON" >> {
          val res = getExtractedResponse(JsonOutput.fileExtension)
          res must provideJsonResponse
        }
        "Text" >> {
          val res = getExtractedResponse(TextOutput.fileExtension)
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
          val res = getExtractedResponse(BashOutput.queryString._2)
          res must provideBashResponse
        }
        "JSON" >> {
          val res = getExtractedResponse(JsonOutput.queryString._2)
          res must provideJsonResponse
        }
        "Text" >> {
          val res = getExtractedResponse(TextOutput.queryString._2)
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
        headers("Content-Type").equals(BashOutput.acceptValue) &&
        response.contains("""Data_TestList_name="foo123";""") &&
        response.contains("""Status="Ok";""");
      result(status, "Got expected Bash response", ("Invalid Bash response for " + s.value), s)
    }
  }

  val provideJsonResponse = new Matcher[ResultTuple] {
    def apply[S <: ResultTuple](s: Expectable[S]) = {
      val code = s.value._1
      val headers = s.value._2
      val response = s.value._3
      val json = parseJson(response)
      val status: Boolean = code == 200 &&
        headers.contains("Content-Type") &&
        headers("Content-Type").equals(JsonOutput.acceptValue) &&
        (json \ "Status").isInstanceOf[JsString] &&
        (json \ "Status").valueAs[String].equals("Ok") &&
        (json \ "Data").isInstanceOf[JsObject] &&
        (json \ "Data" \ "TestList").isInstanceOf[JsArray] &&
        (json \ "Data" \ "TestList")(0).isInstanceOf[JsObject] &&
        ((json \ "Data" \ "TestList")(0) \ "id").valueAs[BigDecimal] == 123;
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
        headers("Content-Type").equals(TextOutput.acceptValue) &&
        response.contains("Status\tOk");
      result(status, "Got expected Text response", ("Invalid text response for " + s.value), s)
    }
  }


}
