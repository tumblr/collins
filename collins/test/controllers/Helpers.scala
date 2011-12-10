package controllers

import models.{User, UserImpl}
import play.api.mvc._

trait SpecHelpers {
  case class MockRequest(
    uri: String = "http://tumblr.com/api/fizz?foo=bar",
    queryString: Map[String,Seq[String]] = Map.empty,
    body: AnyContent = AnyContentAsEmpty,
    path: String = "/api/fizz",
    headers: Seq[String] = Seq.empty
  )

  def getApi(user: Option[User]) = new Api with SecureController {
    override def authenticate(request: RequestHeader) = user
    override def getUser(request: RequestHeader) = user.get
  }
  def getLoggedInUser(group: String) = Some(UserImpl("test", "*", Seq(group), 123, true))

  def getRequest(req: MockRequest) = new Request[AnyContent] {
    def uri = req.uri
    def method = "GET"
    def queryString = req.queryString
    def body = req.body
    def username = Some("testuser")
    def path = req.path
    def headers = new Headers {
      def getAll(key: String) = req.headers
    }
    def cookies = new Cookies {
      def get(name: String) = Some(Cookie(name="foo",value="yay"))
    }
  }
}
