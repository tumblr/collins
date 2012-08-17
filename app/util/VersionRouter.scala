package util

import play.api.mvc.{Request, Headers}
import controllers.actions.SecureAction


/**
 * This enumeration only exists to help keep versions consistent, so we don't
 * accidently route for non-existant versions
 */
object ApiVersion extends Enumeration {
  type ApiVersion = Value
  val `1.0` = Value("1.0")
  val `2.0` = Value("2.0")
  val `2.1` = Value("2.1")

  val defaultVersion = `2.1`

  def safeWithName(name: String): Option[ApiVersion] = try Some(withName(name)) catch {case _ => None}
}
import ApiVersion._

object VersionRouter {

  val acceptHeader = """com.tumblr.collins;version=([0-9]+\.[0-9]+)""".r

  def apply[T](routes: Map[ApiVersion, T])(requestHeaders: Headers): T = requestHeaders
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
    .flatMap{v => routes.get(v).toRight("Unrouted version " + v.toString)}
    .fold(
      err => throw new Exception(err),
      route => route
    )

}

