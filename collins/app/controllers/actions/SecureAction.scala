package controllers
package actions

import models.User
import util.SecuritySpecification

import play.api.Logger
import play.api.libs.concurrent.Promise
import play.api.mvc._

import ApiResponse.formatResponseData

import java.util.concurrent.atomic.AtomicReference

abstract class SecureAction[T](
  val securitySpecification: SecuritySpecification,
  val securityHandler: SecureController
) extends Action[AnyContent] {

  import ActionHelper.DummyRequest

  protected[this] val logger = Logger.logger

  val FeatureMessages = util.Feature.Messages

  // Convert response data to a result
  implicit def rd2res(rd: ResponseData): Result = rd.asResult(request())
  implicit def prd2pres(rd: Promise[ResponseData]): Promise[Result] = rd.map { r =>
    r.asResult(request())
  }

  private val _request = new AtomicReference[Request[AnyContent]](DummyRequest)
  private def setRequest(r: Request[AnyContent]): Unit = _request.set(r)
  def request(): Request[AnyContent] = _request.get()

  private val _user = new AtomicReference[User](User.empty)
  private def setUser(u: User): Unit = _user.set(u)
  def user(): User = _user.get()
  def userOption(): Option[User] = user() match {
    case u if u.isEmpty => None
    case u => Some(u)
  }

  def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    Left(NotImplementedError)
  }

  def execute(rd: RequestDataHolder): Result
  def handleError(rd: RequestDataHolder): ResponseData = {
    Api.getErrorMessage(rd.toString, rd.status().getOrElse(Results.InternalServerError))
  }

  final override def parser: BodyParser[AnyContent] = BodyParsers.parse.anyContent
  final override def apply(req: Request[AnyContent]): Result = {
    setRequest(req)
    checkAuthorization() match {
      case Left(res) => res
      case Right(user) =>
        setUser(user)
        run()
    }
  }

  private def checkAuthorization(): Either[Result,User] = {
    val path = request.path
    if (securitySpecification.isSecure) {
      securityHandler.authenticate(request) match {
        case None =>
          logger.debug("Auth required and NOT successful for %s".format(path))
          Left(securityHandler.onUnauthorized(request))
        case Some(user) =>
          logger.debug("Auth required for %s and requested by %s was successful".format(path, user.username))
          if (!securitySpecification.requiresAuthorization) {
            logger.debug("No credentials required for %s, processing action".format(path))
            Right(user)
          } else if (securityHandler.authorize(user, securitySpecification)) {
            logger.debug("Credentials required and found for resource %s, user %s".format(
              path, user.username
            ))
            Right(user)
          } else {
            logger.info("Credentials required for %s but not found for user %s".format(
              path, user.username
            ))
            Left(securityHandler.onUnauthorized(request))
          }
      }
    } else {
      logger.debug("No authentication required for %s, processing action".format(path))
      Right(User.empty)
    }
  }

  private def run(): Result = validate() match {
    case Left(error) => handleError(error)
    case Right(response) => execute(response)
  }

}
