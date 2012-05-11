package controllers
package actions

import play.api.mvc._

object ActionHelper {
  val DummyRequest = new Request[AnyContent] {
    val uri = "/"
    val path = "/"
    val method = "GET"
    val queryString = Map.empty[String, Seq[String]]
    val headers = new Headers {
      val keys = Set.empty[String]
      def getAll(key: String): Seq[String] = Seq.empty
    }
    val body = AnyContentAsEmpty
  }

  def createRequest(req: Request[AnyContent], finalMap: Map[String, Seq[String]]) =
    new Request[AnyContent] {
      def uri = req.uri
      def path = req.path
      def method = req.method
      def queryString = finalMap
      def headers = req.headers
      def body = req.body
    }
}
