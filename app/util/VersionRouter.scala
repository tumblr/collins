package util

import play.api.mvc.{Request, Headers}
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

  val acceptHeader = """com.tumblr.collins;version=([0-9]+\.[0-9]+)""".r

  def apply[T](requestHeaders: Headers)(routes: PartialFunction[ApiVersion, T]): T = requestHeaders
    .toMap
    //get the accept header
    .get("Accept")
    //match the regex if a header was sent
    .map{_
      .collectFirst{case acceptHeader(version) => version}
      .toRight("invalid Accept header").right
      //verify the version is valid
      .flatMap{version => ApiVersion.safeWithName(version).toRight("Unknown Version " + version)}
    }
    //or provide the default version if no header
    .getOrElse(Right(ApiVersion.defaultVersion)).right
    //find the route for the version
    .map{v => routes(v)}
    .fold(
      err => throw new Exception(err),
      route => route
    )

  def apply[R,T](request: Request[R])(routes: PartialFunction[ApiVersion, T]): T = this(request.headers)(routes)

}

