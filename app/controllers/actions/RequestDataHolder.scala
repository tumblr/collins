package controllers
package actions

import models.User

import play.api.mvc.Results
import play.api.mvc.Results.{Status => HttpStatus}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

trait RequestDataHolder {
  type This = RequestDataHolder

  val ErrorKey = "error.message"
  protected val sMap = new ConcurrentHashMap[String,String]()
  protected val _status = new AtomicReference[Option[HttpStatus]](None)
  protected val _exception = new AtomicReference[Option[Throwable]](None)

  def status(): Option[HttpStatus] = _status.get()
  def status_= (s: HttpStatus):Unit = _status.set(Some(s))

  def error(): Option[String] = string(ErrorKey)
  def exception(): Option[Throwable] = _exception.get()
  def exception_= (e: Throwable) = _exception.set(Some(e))
  def exception_= (e: Option[Throwable]) = _exception.set(e)
  def withException(e: Throwable): This = {
    _exception.set(Some(e))
    this
  }
  def withException(e: Option[Throwable]): This = {
    _exception.set(e)
    this
  }
  def string(k: String): Option[String] = Option(sMap.get(k))
  def string(k: String, default: String): String = string(k).getOrElse(default)
  def update(k: String, v: String): This = {
    sMap.put(k,v)
    this
  }
}

// This exists for staged validation, which may or may not have data to pass between stages
case class EphemeralDataHolder(message: Option[String] = None) extends RequestDataHolder {
  if (message.isDefined) {
    update(ErrorKey, message.get)
  }
}

object RequestDataHolder extends RequestDataHolder {
  private[RequestDataHolder] case class ErrorRequestDataHolder(
    message: String,
    override val status: Option[HttpStatus]
  ) extends RequestDataHolder {
    update(ErrorKey, message)
    override def toString() = message
  }

  object ErrorRequestDataHolder {
    def apply(msg: String, status: HttpStatus) = new ErrorRequestDataHolder(msg, Some(status))
    def apply(msg: String) = new ErrorRequestDataHolder(msg, Some(Results.BadRequest))
  }

  def error4xx(message: String, status: HttpStatus, exception: Option[Throwable]) =
    new ErrorRequestDataHolder(message, Some(status)).withException(exception)

  def error400(message: String): RequestDataHolder =
    error4xx(message, Results.BadRequest, None)
  def error400(message: String, e: Throwable): RequestDataHolder =
    error4xx(message, Results.BadRequest, Some(e))

  def error403(message: String): RequestDataHolder =
    error4xx(message, Results.Forbidden, None)
  def error403(message: String, e: Throwable): RequestDataHolder =
    error4xx(message, Results.Forbidden, Some(e))

  def error404(message: String): RequestDataHolder =
    error4xx(message, Results.NotFound, None)

  def error409(message: String): RequestDataHolder =
    error4xx(message, Results.Conflict, None)

  def error429(message: String): RequestDataHolder =
    error4xx(message, Results.TooManyRequest, None)

  def error5xx(message: String, status: HttpStatus, exception: Option[Throwable]) =
    new ErrorRequestDataHolder(message, Some(status)).withException(exception)

  def error500(message: String, t: Option[Throwable] = None): RequestDataHolder =
    error5xx(message, Results.InternalServerError, t)
  def error500(message: String, t: Throwable): RequestDataHolder =
    error5xx(message, Results.InternalServerError, Some(t))
  def error501(message: String): RequestDataHolder =
    error5xx(message, Results.NotImplemented, None)

  def error504(message: String): RequestDataHolder = ErrorRequestDataHolder(
    message, Results.Status(play.api.http.Status.GATEWAY_TIMEOUT)
  )
}

object NotImplementedError extends RequestDataHolder {
  override def toString() = "Not Implemented"
  override def status() = Some(Results.NotImplemented)
}
