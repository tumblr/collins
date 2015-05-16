package collins.controllers.actions

import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Headers
import play.api.mvc.Request

object ActionHelper {
  val DummyRequest = new Request[AnyContent] {
    def id: Long = 1L
    def tags: Map[String,String] = Map()
    def version: String = "HTTP/1.0"
    val uri = "/"
    val path = "/"
    val method = "GET"
    val queryString = Map.empty[String, Seq[String]]
    val remoteAddress = "127.0.0.1"
    val headers = new Headers {
      protected val data = Seq.empty[(String, Seq[String])]
    }
    val body = AnyContentAsEmpty
  }

  def createRequest(req: Request[AnyContent], finalMap: Map[String, Seq[String]]) =
    new Request[AnyContent] {
      def id: Long = 1L
      def tags: Map[String,String] = Map()
      def version: String = "HTTP/1.0"
      def uri = req.uri
      def path = req.path
      def method = req.method
      def queryString = finalMap
      def headers = req.headers
      def body = req.body
      def remoteAddress = req.remoteAddress
    }
}
