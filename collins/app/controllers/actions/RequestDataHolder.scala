package controllers
package actions

import models.User

import play.api.mvc.Results
import play.api.mvc.Results.{Status => HttpStatus}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

trait RequestDataHolder {
  protected val iMap = new ConcurrentHashMap[String,Int]()
  protected val sMap = new ConcurrentHashMap[String,String]()
  protected val _status = new AtomicReference[Option[HttpStatus]](None)

  def status(): Option[HttpStatus] = _status.get()
  def status_= (s: HttpStatus):Unit = _status.set(Some(s))

  def int(k: String): Option[Int] = Option(iMap.get(k))
  def int(k: String, default: Int): Int = int(k).getOrElse(default)
  def update(k: String, v: Int): Unit = iMap.put(k,v)

  def string(k: String): Option[String] = Option(sMap.get(k))
  def string(k: String, default: String): String = string(k).getOrElse(default)
  def update(k: String, v: String): Unit = sMap.put(k,v)
}

object RequestDataHolder extends RequestDataHolder {
  private[RequestDataHolder] case class ErrorRequestDataHolder(
    message: String,
    override val status: Option[HttpStatus]
  ) extends RequestDataHolder {
    override def toString() = message
  }

  object ErrorRequestDataHolder {
    def apply(msg: String, status: HttpStatus) = new ErrorRequestDataHolder(msg, Some(status))
    def apply(msg: String) = new ErrorRequestDataHolder(msg, Some(Results.BadRequest))
  }

  def error404(message: String): RequestDataHolder = ErrorRequestDataHolder(
    message, Results.NotFound
  )
  def error500(message: String): RequestDataHolder = ErrorRequestDataHolder(
    message, Results.InternalServerError
  )
  def error400(message: String): RequestDataHolder = ErrorRequestDataHolder(
    message, Results.BadRequest
  )
}

object NotImplementedError extends RequestDataHolder {
  override def toString() = "Not Implemented"
  override def status() = Some(Results.NotImplemented)
}
