package controllers

import actions.asset.{CreateAction, DeleteAction, DeleteAttributeAction, FindAction, GetAction}
import actions.asset.{UpdateAction, UpdateForMaintenanceAction}

import views.html
import models.{Status => AStatus}
import models._
import util._

import play.api.data._
import play.api.data.Forms._
import play.api.http.{Status => StatusValues}
import play.api.mvc._
import play.api.libs.json._

import java.util.Date

trait AssetApi {
  this: Api with SecureController =>

  // GET /api/asset/:tag
  def getAsset(tag: String, location: Option[String] = None) = {
  logger.debug(location.toString)
  location match {
    case Some(locationTag) if (
      Config.getBoolean("multicollins.enabled").getOrElse(false) &&
      Config.getString("multicollins.thisInstance","NONE") != locationTag
    ) => Action { request => 
      implicit val req = request
      Asset.findByTag(locationTag) match {
        case Some(asset) => asset.getMetaAttribute("LOCATION").map{_.getValue} match {
          case Some(location) => MovedPermanently(location.split(";")(0) + app.routes.Api.getAsset(tag, None))
          case None => Api.errorResponse("No Location attribute for remote instance %s".format(locationTag), Results.InternalServerError).asResult
        }
        case None => Api.errorResponse("Unknown location %s".format(locationTag), Results.NotFound).asResult
      }
    } 
    case _ => GetAction(tag, Permissions.AssetApi.GetAsset, this)
  }}

  // GET /api/assets?params
  def getAssets(page: Int, size: Int, sort: String) = FindAction(
    PageParams(page, size, sort), Permissions.AssetApi.GetAssets, this
  )

  // PUT /api/asset/:tag
  def createAsset(tag: String) = CreateAction(Some(tag), None, Permissions.AssetApi.CreateAsset, this)

  // POST /api/asset/:tag
  def updateAsset(tag: String) = UpdateAction(tag, Permissions.AssetApi.UpdateAsset, this)

  def updateAssetForMaintenance(tag: String) = UpdateForMaintenanceAction(
    tag, Permissions.AssetApi.UpdateAssetForMaintenance, this
  )

  // DELETE /api/asset/attribute/:attribute/:tag
  def deleteAssetAttribute(tag: String, attribute: String) = DeleteAttributeAction(
    tag, attribute, Permissions.AssetApi.DeleteAssetAttribute, this
  )

  // DELETE /api/asset/:tag
  def deleteAsset(tag: String) = DeleteAction(tag, Permissions.AssetApi.DeleteAsset, this)

}
