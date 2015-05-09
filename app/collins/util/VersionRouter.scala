package collins.util

import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.Headers
import play.api.mvc.Results

import collins.controllers.ApiResponse
import collins.controllers.actions.SecureAction

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

class VersionException(message: String) extends Exception(message)

object VersionRouter {

  val acceptHeader = """application/com.tumblr.collins;version=([0-9]+\.[0-9]+)""".r

  def routeEither[T](requestHeaders: Headers)(routes: Function1[ApiVersion, T]): Either[String, T] = {
    val heads = try {
      //get the accept header as sequence
      requestHeaders.getAll("Accept")
    } catch {
      case n: NoSuchElementException => Nil
    }
    val apiVersion = heads
      // accept headers can be comma separated or multiple headers, convert to flattened list
      .flatMap(_.split(","))
      // find first matching accept header
      .collectFirst { case acceptHeader(version) => version }
      // or if unspecified, use the default
      .getOrElse(ApiVersion.defaultVersion.stringName)
    ApiVersion.safeWithName(apiVersion).toRight("Unknown API version " + apiVersion)
      .right
      .map(v => routes(v))
  }

  def route[T](requestHeaders: Headers)(routes: ApiVersion => T): T = routeEither(requestHeaders)(routes).fold(
    err => throw new VersionException(err),
    route => route
  )

  def apply(routes: Function1[ApiVersion, SecureAction]): Action[AnyContent] = Action{implicit request =>
    routeEither(request.headers)(routes).fold(
      err => if (OutputType.isHtml(request)) {
        Results.Redirect(collins.app.routes.Resources.index).flashing("error" -> err)
      } else {
        Results.BadRequest(ApiResponse.formatJsonError(err, None))
      },
      action => action(request)
    )
  }

}

