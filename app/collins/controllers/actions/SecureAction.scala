package collins.controllers.actions

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.Promise

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.BodyParser
import play.api.mvc.BodyParsers
import play.api.mvc.Call
import play.api.mvc.Flash
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.Results

import collins.controllers.Api
import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.models.User
import collins.util.OutputType
import collins.util.security.SecuritySpecification

import ActionHelper.DummyRequest

// Override execute and validate, optionally handleError or handleWebError (if you support HTML
// views)
abstract class SecureAction(
  val securitySpecification: SecuritySpecification,
  val securityHandler: SecureController
) extends Action[AnyContent] {

  type Validation = Either[RequestDataHolder,RequestDataHolder]

  protected[this] val logger = Logger.logger

  val FeatureMessages = collins.util.config.Feature.Messages
  val Status = Results
  def Redirect(call: Call) = Results.Redirect(call)

  // Convert response data to a result
  implicit def rd2res(rd: ResponseData): Result = rd.asResult(request())
  implicit def prd2pres(prd: Future[ResponseData]): Future[Result] = prd.map { r =>
    r.asResult(request())
  }
  implicit def jss2jso(jss: Seq[(String, JsValue)]): JsObject = JsObject(jss)

  private val _request = new AtomicReference[Request[AnyContent]](DummyRequest)
  private def setRequest(r: Request[AnyContent]): Unit = _request.set(r)
  protected def request(): Request[AnyContent] = _request.get()
  protected def flash(): Flash = request.flash

  private val _user = new AtomicReference[User](User.empty)
  private def setUser(u: User): Unit = _user.set(u)
  def user(): User = _user.get()
  def userOption(): Option[User] = user() match {
    case u if u.isEmpty => None
    case u => Some(u)
  }

  def validateRead(): Option[Validation] = None
  def validateWrite(): Option[Validation] = None
  def validateCreate(): Option[Validation] = None
  def validateDelete(): Option[Validation] = None
  def validate(): Validation

  def executeRead(rd: RequestDataHolder): Option[Future[Result]] = None
  def executeWrite(rd: RequestDataHolder): Option[Future[Result]] = None
  def executeCreate(rd: RequestDataHolder): Option[Future[Result]] = None
  def executeDelete(rd: RequestDataHolder): Option[Future[Result]] = None
  def execute(rd: RequestDataHolder): Future[Result]

  def handleError(rd: RequestDataHolder): Result = {
    val htmlOutput = isHtml match {
      case true => handleWebError(rd)
      case false => None
    }
    htmlOutput.getOrElse(
      Api.errorResponse(
        rd.toString,
        rd.status().getOrElse(Results.InternalServerError),
        rd.exception()
      )
    )
  }

  def handleWebError(rd: RequestDataHolder): Option[Result] = None

  final override def parser: BodyParser[AnyContent] = BodyParsers.parse.anyContent
  final override def apply(req: Request[AnyContent]) =  {
    setRequest(req)
    checkAuthorization() match {
      case Left(res) => res
      case Right(user) => {
        setUser(user)
        run()
      }
    }
  }

  protected def getHeaders(): Seq[Tuple2[String,String]] = Seq(
    "Content-Language" -> "en"
  )

  protected def isHtml(): Boolean = OutputType.isHtml(request)
  protected def isReadRequest(): Boolean = request.method == "GET" || request.method == "HEAD"
  protected def isWriteRequest(): Boolean = request.method == "POST"
  protected def isCreateRequest(): Boolean = request.method == "PUT"
  protected def isDeleteRequest(): Boolean = request.method == "DELETE"

  private def checkAuthorization(): Either[Future[Result],User] = {
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

  private def handleValidation(): Validation = {
    val validationResults =
      if (isReadRequest)
        validateRead()
      else if (isWriteRequest)
        validateWrite()
      else if (isCreateRequest)
        validateCreate()
      else if (isDeleteRequest)
        validateDelete()
      else
        None
    validationResults.getOrElse(validate())
  }

  private def handleExecution(rd: RequestDataHolder): Future[Result] = {
    val executionResults =
      if (isReadRequest)
        executeRead(rd)
      else if (isWriteRequest)
        executeWrite(rd)
      else if (isCreateRequest)
        executeCreate(rd)
      else if (isDeleteRequest)
        executeDelete(rd)
      else
        None
    executionResults.getOrElse(execute(rd))
  }

  private def run(): Future[Result] = handleValidation() match {
    case Left(rd) => Promise.successful(handleError(rd)).future
    case Right(rd) => handleExecution(rd) map {
      case p: Result => p.withHeaders(getHeaders:_*)
      case o => o
    }
  }

  def tattler = securityHandler.tattler(userOption())
}
