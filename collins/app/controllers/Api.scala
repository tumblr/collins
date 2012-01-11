package controllers

import models.Asset
import util._

import play.api._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import java.io.File
import java.util.Date

private[controllers] case class ResponseData(status: Results.Status, data: JsObject, headers: Seq[(String,String)] = Nil, attachment: Option[AnyRef] = None) {
  def map[T](fn: ResponseData => T) = fn(this)
}

trait Api extends ApiResponse with AssetApi with AssetLogApi {
  this: SecureController =>

  protected implicit val securitySpec = SecuritySpec(isSecure = true, Nil)

  def ping = Action { implicit req =>
    formatResponseData(ResponseData(Results.Ok, JsObject(Seq(
      "Data" -> JsObject(Seq(
        "Timestamp" -> JsString(Helpers.dateFormat(new Date())),
        "TestObj" -> JsObject(Seq(
          "TestString" -> JsString("test"),
          "TestList" -> JsArray(List(JsNumber(1), JsNumber(2)))
        )),
        "TestList" -> JsArray(List(
          JsObject(Seq("id" -> JsNumber(123), "name" -> JsString("foo123"))),
          JsObject(Seq("id" -> JsNumber(124), "name" -> JsString("foo124"))),
          JsObject(Seq("id" -> JsNumber(124), "name" -> JsString("foo124")))
        ))
      )),
      "Status" -> JsString("Ok")
    ))))
  }

  def errorPing(id: Int) = Action { implicit req =>
    req.queryString.map { case(k,v) =>
      k match {
        case "foo" =>
          formatResponseData(ResponseData(Results.Ok, JsObject(Seq("Result" -> JsBoolean(true)))))
      }
    }.head
  }
}

object Api {
  def withAssetFromTag[T](tag: String)(f: Asset => Either[ResponseData,T]): Either[ResponseData,T] = {
    Asset.isValidTag(tag) match {
      case false => Left(getErrorMessage("Invalid tag specified"))
      case true => Asset.findByTag(tag) match {
        case Some(asset) => f(asset)
        case None => Left(getErrorMessage("Could not find specified asset", Results.NotFound))
      }
    }
  }

  def getErrorMessage(msg: String, status: Results.Status = Results.BadRequest, ex: Option[Throwable] = None) = {
    val json = ApiResponse.formatJsonError(msg, ex)
    ResponseData(status, json)
  }
}
