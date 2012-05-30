package controllers

import actions.logs.{CreateAction, FindAction}

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
  def submitLogData(tag: String) =
    CreateAction(tag, spec = Permissions.AssetLogApi.Create, handler = this)

}
