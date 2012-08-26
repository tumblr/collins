package util

import play.api.mvc._
import controllers.actions.SecureAction


/**
 * We're using an algebraic data type and partial functions for the routing map to ensure
 * 1 - we don't accidentally route to a non-existant version
 * 2 - we make sure every router has a defined path for every version
 *
 * with this implementation, the compiler can check for both of these
 */

sealed trait ApiVersion {
  def stringName: String
}

case object `1.1` extends ApiVersion {
  def stringName = "1.1"
}
case object `1.2` extends ApiVersion {
  def stringName = "1.2"
}

object ApiVersion {
  val versions = Set(`1.1`, `1.2`)
  val defaultVersion = `1.1`

  def safeWithName(name: String): Option[ApiVersion] = versions.find(_.stringName == name)
}
import ApiVersion._

object VersionRouter {

  val acceptHeader = """application/com.tumblr.collins;version=([0-9]+\.[0-9]+)""".r

  def route[T](requestHeaders: Headers)(routes: Function1[ApiVersion, T]): T = {
    val apiVersion = requestHeaders
      //get the accept header as sequence
      .getAll("Accept")
      // accept headers can be comma separated or multiple headers, convert to flattened list
      .flatMap(_.split(","))
      // find first matching accept header
      .collectFirst { case acceptHeader(version) => version }
      // or if unspecified, use the default
      .getOrElse(ApiVersion.defaultVersion.stringName)
    ApiVersion.safeWithName(apiVersion).toRight("Unknown API version " + apiVersion)
      .right
      .map(v => routes(v))
      .fold(
        err => throw new Exception(err),
        route => route
      )
  }

  def apply(routes: Function1[ApiVersion, SecureAction]): Action[AnyContent] = Action{implicit request =>
    this.route(request.headers)(routes)(request)
  }

}

