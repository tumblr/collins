package controllers

import actions.logs.FindAction

import models.{Asset, AssetLog, Model, PageParams}
import models.{LogMessageType, LogFormat, LogSource}
import util.SecuritySpec

import play.api.data._
import play.api.data.Forms._
import play.api.libs.json._
import play.api.mvc._
import Results._

import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

trait AssetLogApi {
  this: Api with SecureController =>

  val DefaultMessageType = LogMessageType.Informational

  // GET /assets/:tag/logs
  def getLogData(tag: String, page: Int, size: Int, sort: String, filter: String) =
    FindAction(Some(tag), PageParams(page, size, sort), filter, Permissions.AssetLogApi.Get, this)

  // GET /assets/logs
  def getAllLogData(page: Int, size: Int, sort: String, filter: String) =
    FindAction(None, PageParams(page, size, sort), filter, Permissions.AssetLogApi.GetAll, this)

  // PUT /api/asset/:tag/log
  def submitLogData(tag: String) = Authenticated { user => Action { implicit req =>

    val username = user.get.username

    def processJson(jsValue: JsValue, asset: Asset): Option[String] = {
      val typeString: String = jsValue \ "Type" match {
        case JsString(s) => s.toUpperCase
        case JsUndefined(msg) => DefaultMessageType.toString
        case _ => return Some("Type must be a string")
      }
      val msg = jsValue \ "Message" match {
        case JsUndefined(msg) => return Some("Didn't find Message in json object")
        case js => js
      }
      try {
        val mtype = LogMessageType.withName(typeString)
        AssetLog.create(
          AssetLog(asset, Json.stringify(msg), LogFormat.Json, LogSource.Api, mtype)
        )
        None
      } catch {
        case _ => Some("Invalid message type specified. Valid types are: %s".format(
          LogMessageType.values.mkString(","))
        )
      }
    }

    def processForm(asset: Asset): Option[String] = {
      Form(tuple(
            "message" -> nonEmptyText,
            "type" -> optional(text(1)),
            "source" -> optional(text(1))
           )
      ).bindFromRequest.fold(
        error => Some("Message must not be empty"),
        success => {
          val (f_msg, f_type, f_source) = success
          val typeString = f_type.getOrElse(DefaultMessageType.toString).toUpperCase
          val source = LogSource.withName(f_source.map(s => "USER").getOrElse("API"))
          val escapedMessage = Jsoup.clean(f_msg, Whitelist.basicWithImages())
          val msg = "%s: %s".format(username, escapedMessage)
          try {
            val mtype = LogMessageType.withName(typeString)
            AssetLog.create(
              AssetLog(asset, msg, LogFormat.PlainText, source, mtype)
            )
            None
          } catch {
            case _ => Some("Invalid message type specified. Valid types are: %s".format(
              LogMessageType.values.mkString(","))
            )
          }
        }
      )
    }

    val responseData = Api.withAssetFromTag(tag) { asset =>
      (req.body.asJson match {
        case Some(jsValue) => processJson(jsValue, asset)
        case None => processForm(asset)
      }).map { err =>
        Left(Api.getErrorMessage(err))
      }.getOrElse {
        Right(Api.statusResponse(true, Results.Created))
      }
    }.fold(l => l, r => r)
    formatResponseData(responseData)
  }}(Permissions.AssetLogApi.Create)


}
