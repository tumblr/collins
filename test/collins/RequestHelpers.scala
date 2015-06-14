package collins

import org.specs2.matcher.Expectable
import org.specs2.matcher.Matcher
import org.specs2.specification.Scope

import play.api.http.HeaderNames
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.RequestHeader
import play.api.test.FakeHeaders
import play.api.test.{FakeRequest => PlayFakeRequest}

object FakeRequestHeader {
  def apply[A](req: PlayFakeRequest[A]) = new FakeRequestHeader(
    method = req.method,
    uri = req.uri,
    headers = req.headers
  )
  def withAcceptHeader(value: String): FakeRequestHeader = {
    FakeRequestHeader(headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> Seq(value))))
  }
}

case class FakeRequestHeader(
  method: String = "GET",
  uri: String = "/",
  headers: FakeHeaders = FakeHeaders()
) extends RequestHeader {
  def id: Long = 667L
  def tags: Map[String,String] = Map()
  def version: String = "HTTP/1.1"
  lazy val path = uri.split('?').take(1).mkString
  lazy val queryString = play.core.parsers.FormUrlEncodedParser.parse(rawQueryString)
  val remoteAddress = "127.0.0.1"
  def secure = false
  def asRequest(body: AnyContent = AnyContentAsEmpty) =
    PlayFakeRequest(
      this.method,
      this.uri,
      this.headers,
      body
    )
}

object FakeRequest {
  def apply(): PlayFakeRequest[AnyContentAsEmpty.type] = {
    PlayFakeRequest()
  }
  def apply(method: String, uri: String): PlayFakeRequest[AnyContentAsEmpty.type] = {
    PlayFakeRequest(method, uri)
  }
  def apply[A](method: String, uri: String, body: A) = {
    PlayFakeRequest(method, uri, FakeHeaders(), body)
  }  
  def apply[A](body: A) = 
    PlayFakeRequest("GET", "/", FakeHeaders(), body)
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
      val data = (parsed \ "data")
      val matchResult = data.isInstanceOf[JsObject] || data.isInstanceOf[JsArray]
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
          result(f(txt), "ok", "ko", a)
        } else {
          outerResult
        }
      }
    }
  }

  def haveJsonData() = new JsonDataMatcher {}

}

trait ResponseScope extends Scope with ResponseMatchHelpers
