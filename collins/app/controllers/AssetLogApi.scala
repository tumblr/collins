package controllers

import models.{Asset, AssetLog, Model}
import util.{Helpers, SecuritySpec}

import play.api.data._
import play.api.json._

trait AssetLogApi {
  this: Api with SecureController =>

  // GET /api/asset/:tag/log
  def getLogData(tag: String, page: Int, size: Int, sort: String) = SecureAction { implicit req =>
    val responseData = withAssetFromTag(tag) { asset =>
      val logs = AssetLog.list(asset, page, size, sort)
      val logMessage: AssetLog => JsValue = { log =>
        log.is_json match {
          case true => parseJson(log.message)
          case false => JsString(log.message)
        }
      }
      val prevPage = logs.prev match {
        case None => 0
        case Some(n) => n
      }
      val nextPage = logs.next match {
        case None => page
        case Some(n) => n
      }
      val totalResults = logs.total
      val headers = Seq(
        ("X-Pagination-PreviousPage" -> prevPage.toString),
        ("X-Pagination-CurrentPage" -> page.toString),
        ("X-Pagination-NextPage" -> nextPage.toString),
        ("X-Pagination-TotalResults" -> totalResults.toString)
      )
      ResponseData(Ok, JsObject(Map(
        "Pagination" -> JsObject(Map(
          "PreviousPage" -> JsNumber(prevPage),
          "CurrentPage" -> JsNumber(page),
          "NextPage" -> JsNumber(nextPage),
          "TotalResults" -> JsNumber(logs.total)
        )),
        "Data" -> JsArray(logs.items.map { log =>
          JsObject(Map(
            "AssetTag" -> JsString(tag),
            "Created" -> JsString(Helpers.dateFormat(log.created)),
            "IsError" -> JsBoolean(log.is_error),
            "Message" -> logMessage(log)
          ))
        }.toList)
      )), headers)
    }
    formatResponseData(responseData)
  }

  // PUT /api/asset/:tag/log
  def submitLogData(tag: String) = SecureAction { implicit req =>

    def processJson(jsValue: JsValue, asset: Asset): Option[String] = {
      val is_error: Boolean = jsValue \ "IsError" match {
        case JsBoolean(bool) => bool
        case JsUndefined(msg) => false
        case _ => return Some("IsError must be a boolean")
      }
      val msg = jsValue \ "Message" match {
        case JsUndefined(msg) => return Some("Didn't find Message in json object")
        case js => js
      }
      Model.withConnection { implicit con =>
        AssetLog.create(AssetLog(asset, msg, is_error))
      }
      None
    }

    def processForm(asset: Asset): Option[String] = {
      Form(of(
            "message" -> requiredText(1),
            "is_error" -> optional(boolean)
           )
      ).bindFromRequest.fold(
        error => {
          val msg = error.errors.map { _.message }.mkString(", ")
          Some(msg)
        },
        success => {
          val msg = success._1
          val is_error = success._2.getOrElse(false)
          Model.withConnection { implicit con =>
            AssetLog.create(AssetLog(asset, msg, is_error))
          }
          None
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
