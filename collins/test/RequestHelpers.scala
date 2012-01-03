package test

import org.specs2.matcher._

import play.api.mvc._
import play.api.http.HeaderNames
import play.api.test.{FakeRequest => PlayFakeRequest}
import play.api.test._

object FakeRequestHeader {
  def apply[A](req: PlayFakeRequest[A]) = new FakeRequestHeader(
    method = req.method,
    uri = req.uri,
    headers = req.headers,
    cookies = req.cookies
  )
  def withAcceptHeader(value: String) = {
    FakeRequestHeader(FakeRequest()).copy(headers = FakeHeaders(Map(HeaderNames.ACCEPT -> Seq(value))))
  }
}

case class FakeRequestHeader(
  method: String = "GET",
  uri: String = "/",
  headers: FakeHeaders = FakeHeaders(),
  cookies: FakeCookies = FakeCookies()
) extends RequestHeader {
  lazy val path = uri.split('?').take(1).mkString
  lazy val queryString = play.core.parsers.UrlFormEncodedParser.parse(rawQueryString)
  def asRequest(body: AnyContent = AnyContentAsEmpty) =
    PlayFakeRequest(
      this.method,
      this.uri,
      this.headers,
      this.cookies,
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
