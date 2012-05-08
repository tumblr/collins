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
}
