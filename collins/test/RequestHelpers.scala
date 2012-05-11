package test

import org.specs2._
import specification._
import matcher._

import play.api.mvc._
import play.api.http.HeaderNames
import play.api.test.{FakeRequest => PlayFakeRequest}
import play.api.test._

object FakeRequestHeader {
  def apply[A](req: PlayFakeRequest[A]) = new FakeRequestHeader(
    method = req.method,
    uri = req.uri,
    headers = req.headers
  )
  def withAcceptHeader(value: String) = {
    FakeRequestHeader(FakeRequest()).copy(headers = FakeHeaders(Map(HeaderNames.ACCEPT -> Seq(value))))
  }
}

case class FakeRequestHeader(
  method: String = "GET",
  uri: String = "/",
  headers: FakeHeaders = FakeHeaders()
) extends RequestHeader {
  lazy val path = uri.split('?').take(1).mkString
  lazy val queryString = play.core.parsers.FormUrlEncodedParser.parse(rawQueryString)
  def asRequest(body: AnyContent = AnyContentAsEmpty) =
    PlayFakeRequest(
      this.method,
      this.uri,
      this.headers,
      body
    )
}

object FakeRequest {
  def apply(): PlayFakeRequest[AnyContent] = {
    PlayFakeRequest()
  }
  def apply(method: String, uri: String): PlayFakeRequest[AnyContent] = {
    PlayFakeRequest(method, uri)
  }
  def apply[A](body: A) = PlayFakeRequest().copy(body = body)
}

object ResultType {
  type ResultTuple = Tuple3[Int, Map[String,String], String]
}

abstract class ResponseMatcher(contentType: String) extends Matcher[ResultType.ResultTuple] {
  def expectedStatusCode: Int
  def responseMatches(txt: String): Boolean
  def apply[S <: ResultType.ResultTuple](s: Expectable[S]) = {
    val (code, headers, response) = s.value
    val status: Boolean = code == expectedStatusCode &&
      headers.contains("Content-Type") &&
      headers("Content-Type").contains(contentType) &&
      responseMatches(response);
    result(
      status,
      "Got expected %s response".format(contentType),
      "Invalid %s response for %s".format(contentType, s.value),
      s
    )
  }
}

trait ResponseMatchHelpers {
  import ResultType.ResultTuple
  import play.api.libs.json._

  def haveStatus(expectedCode: Int) = new Matcher[ResultTuple] {
    def apply[S <: ResultTuple](s: Expectable[S]) = {
      val code = s.value._1
      result(code == expectedCode,
        "expected and got %d".format(expectedCode),
        "expected %d, got %d".format(expectedCode, code),
        s
      )
    }
  }

  trait JsonDataMatcher extends Matcher[ResultTuple] { outer =>
    def apply[S <: ResultTuple](s: Expectable[S]) = {
      val response = s.value._3
      val parsed = Json.parse(response)
      val matchResult = (parsed \ "data").isInstanceOf[JsObject]
      result(matchResult,
        "Response is JSON and contains data key",
        "Response is not JSON or does not contain a 'data' key: %s".format(response),
        s
      )
    }
    def which(f: String => Boolean) = new Matcher[ResultTuple] {
      def apply[S <: ResultTuple](a: Expectable[S]) = {
        val outerResult = outer.apply(a)
        if (outerResult.isSuccess) {
          val txt = a.value._3
          outerResult and result(f(txt), "ok", "ko", a)
        } else {
          outerResult
        }
      }
    }
  }

  def haveJsonData() = new JsonDataMatcher {}

}

trait ResponseScope extends Scope with ResponseMatchHelpers
