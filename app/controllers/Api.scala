package controllers

import actors._
import models.Asset
import util._
import util.concurrent.BackgroundProcessor
import util.views.Formatter.dateFormat

import play.api._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import java.io.File
import java.util.Date

private[controllers] case class ResponseData(status: Results.Status, data: JsValue, headers: Seq[(String,String)] = Nil, attachment: Option[AnyRef] = None) {
  def asResult(implicit req: Request[AnyContent]): Result =
    ApiResponse.formatResponseData(this)(req)
}

trait Api extends ApiResponse with AssetApi with AssetManagementApi with AssetWebApi with AssetLogApi with IpmiApi with TagApi with IpAddressApi with AssetStateApi {
  this: SecureController =>

  lazy protected implicit val securitySpec = Permissions.LoggedIn

  def timestamp = Action { implicit req =>
    val time: Long = System.currentTimeMillis / 1000
    OutputType(req).getOrElse(TextOutput()) match {
      case o: TextOutput =>
        Results.Ok(time.toString).as(o.contentType)
      case o: BashOutput =>
        Results.Ok("TIMESTAMP=%s".format(time.toString)).as(o.contentType)
      case o: JsonOutput =>
        val json = JsObject(Seq("timestamp" -> JsNumber(time)))
        Results.Ok(Json.stringify(json)).as(o.contentType)
      case o: HtmlOutput =>
        Results.Ok(time.toString).as(o.contentType)
    }
  }

  def ping = Action { implicit req =>
    formatResponseData(ResponseData(Results.Ok, JsObject(Seq(
      "Data" -> JsObject(Seq(
        "Timestamp" -> JsString(dateFormat(new Date())),
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

  def asyncPing(sleepMs: Long) = Action { implicit req =>
    AsyncResult {
      BackgroundProcessor.send(TestProcessor(sleepMs)) { case(ex, res) =>
        println("Got result %s".format(res.toString))
        formatResponseData(Api.statusResponse(res.getOrElse(false)))
      }
    }
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

  def statusResponse(status: Boolean, code: Results.Status = Results.Ok) =
    ResponseData(code, JsObject(Seq("SUCCESS" -> JsBoolean(status))))

  def errorResponse(m: String, s: Results.Status = Results.BadRequest, e: Option[Throwable] = None) =
    getErrorMessage(m, s, e)

  def getErrorMessage(msg: String, status: Results.Status = Results.BadRequest, exception: Option[Throwable] = None) = {
    val json = ApiResponse.formatJsonError(msg, exception)
    ResponseData(status, json)
  }
}
