package controllers

import models.{Asset, AssetLog, Model}
import util.{Helpers, SecuritySpec}

import play.api.data._
import play.api.json._

trait AssetLogApi {
  this: Api with SecureController =>

  val DefaultMessageType = AssetLog.MessageTypes.Informational

  // GET /api/asset/:tag/log
  def getLogData(tag: String, page: Int, size: Int, sort: String, filter: String) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      val logs = AssetLog.list(asset, page, size, sort, filter)
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
      val paginationMap = logs.getPaginationJsMap()
      ResponseData(Ok, JsObject(paginationMap ++ Map(
        "Data" -> JsArray(logs.items.map { log =>
          JsObject(log.toJsonMap)
        }.toList)
      )), headers)
    }
    formatResponseData(responseData)
  }

  // PUT /api/asset/:tag/log
  def submitLogData(tag: String) = SecureAction { implicit req =>

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
            AssetLog(asset, stringify(msg), AssetLog.Formats.Json, AssetLog.Sources.Api, mtype)
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
            "type" -> optional(text(1))
           )
      ).bindFromRequest.fold(
        error => {
          val msg = error.errors.map { _.message }.mkString(", ")
          Some(msg)
        },
        success => {
          val msg = success._1
          val typeString = success._2.getOrElse(DefaultMessageType.toString).toUpperCase
          try {
            val mtype = AssetLog.MessageTypes.withName(typeString)
            Model.withConnection { implicit con =>
              AssetLog.create(
                AssetLog(asset, msg, AssetLog.Formats.PlainText, AssetLog.Sources.Api, mtype)
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

    val responseData = withAssetFromTag(tag) { asset =>
      (req.body.asJson match {
        case Some(jsValue) => processJson(jsValue, asset)
        case None => processForm(asset)
      }).map { err =>
        getErrorMessage(err)
      }.getOrElse {
        ResponseData(Created, JsObject(Map("Success" -> JsBoolean(true))))
      }
    }
    formatResponseData(responseData)
  }(SecuritySpec(isSecure = true, Seq("infra")))


}
