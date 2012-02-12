package controllers

import models.{Asset, AssetLog, Model}
import util.{Helpers, SecuritySpec}

import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import Results._

import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist

trait AssetLogApi {
  this: Api with SecureController =>

  val DefaultMessageType = AssetLog.MessageTypes.Informational

  // GET /api/asset/:tag/log
  def getLogData(tag: String, page: Int, size: Int, sort: String, filter: String) = SecureAction { implicit req =>
    val result = Api.withAssetFromTag(tag) { asset =>
      val logs = AssetLog.list(Some(asset), page, size, sort, filter)
      val prevPage = logs.prev match {
        case None => 0
        case Some(n) => n
      }
      val nextPage = logs.next match {
        case None => page
        case Some(n) => n
      }
      val totalResults = logs.total
      val headers = logs.getPaginationHeaders()
      val paginationMap = logs.getPaginationJsObject
      Right(ResponseData(Results.Ok, JsObject(paginationMap ++ Seq(
        "Data" -> JsArray(logs.items.map { log =>
          JsObject(log.forJsonObject)
        }.toList)
      )), headers))
    }
    formatResponseData(result.fold(l => l, r => r))
  }

  // GET /assets/logs
  def getAllLogData(page: Int, size: Int, sort: String, filter: String) = SecureAction { implicit req =>
    val result = {
      val logs = AssetLog.list(None, page, size, sort, filter)
      val prevPage = logs.prev match {
        case None => 0
        case Some(n) => n
      }
      val nextPage = logs.next match {
        case None => page
        case Some(n) => n
      }
      val totalResults = logs.total
      val headers = logs.getPaginationHeaders()
      val paginationMap = logs.getPaginationJsObject
      ResponseData(Results.Ok, JsObject(paginationMap ++ Seq(
        "Data" -> JsArray(logs.items.map { log =>
          JsObject(log.forJsonObject)
        }.toList)
      )), headers)
    }
    formatResponseData(result)
  }

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
        val mtype = AssetLog.MessageTypes.withName(typeString)
        Model.withConnection { implicit con =>
          AssetLog.create(
            AssetLog(asset, Json.stringify(msg), AssetLog.Formats.Json, AssetLog.Sources.Api, mtype)
          )
        }
        None
      } catch {
        case _ => Some("Invalid message type specified. Valid types are: %s".format(
          AssetLog.MessageTypes.values.mkString(","))
        )
      }
    }

    def processForm(asset: Asset): Option[String] = {
      Form(of(
            "message" -> requiredText(1),
            "type" -> optional(text(1)),
            "source" -> optional(text(1))
           )
      ).bindFromRequest.fold(
        error => Some("Message must not be empty"),
        success => {
          val typeString = success._2.getOrElse(DefaultMessageType.toString).toUpperCase
          val source = AssetLog.Sources.withName(success._3.map(s => "USER").getOrElse("API"))
          val escapedMessage = Jsoup.clean(success._1, Whitelist.basicWithImages())
          val msg = if (source == AssetLog.Sources.User) {
            "%s: %s".format(username, escapedMessage)
          } else {
            escapedMessage
          }
          try {
            val mtype = AssetLog.MessageTypes.withName(typeString)
            Model.withConnection { implicit con =>
              AssetLog.create(
                AssetLog(asset, msg, AssetLog.Formats.PlainText, source, mtype)
              )
            }
            None
          } catch {
            case _ => Some("Invalid message type specified. Valid types are: %s".format(
              AssetLog.MessageTypes.values.mkString(","))
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
        Right(ResponseData(Results.Created, JsObject(Seq("SUCCESS" -> JsBoolean(true)))))
      }
    }.fold(l => l, r => r)
    formatResponseData(responseData)
  }}(SecuritySpec(isSecure = true, Seq("infra")))


}
