package controllers

import forms._
import models.{Status => AStatus}
import models._
import util._

import play.api.mvc._
import play.api.libs.json._
import play.api.data._

import com.tumblr.play.{PowerAction, PowerOff, PowerOn, PowerSoft, PowerState, RebootSoft, RebootHard}

trait AssetManagementApi {
  this: Api with SecureController =>

  object PowerHelper {
    def onSuccess(asset: Asset, msg: String, user: Option[User], action: PowerAction) = {
      if (action == PowerState) {
        UserTattler.notice(asset, user, msg)
      } else {
        UserTattler.warning(asset, user, msg)
      }
      Api.statusResponse(true)
    }
    def onFailure(asset: Asset, msg: String, user: Option[User], action: PowerAction) = {
      if (action == PowerState) {
        UserTattler.notice(asset, user, msg)
      } else {
        UserTattler.warning(asset, user, msg)
      }
      Api.getErrorMessage(msg)
    }
    def hasPermissions(asset: Asset, action: PowerAction): Option[String] = {
      if (AppConfig.ignoreAsset(asset)) {
        Some("Specified asset has been configured to not allow this operation")
      } else if (!PowerManagement.assetTypeAllowed(asset)) {
        Some("Asset has no power management configuration")
      } else if (!PowerManagement.actionAllowed(asset, action)) {
        Some("%s is not permitted when asset is Allocated".format(action.toString))
      } else {
        None
      }
    }
  }

  def powerStatus(tag: String) = Authenticated { user => Action { implicit req =>
    val response = PowerManagement.pluginEnabled { plugin =>
      Asset.findByTag(tag).map { asset =>
        plugin.powerState(asset)() match {
          case success if success.isSuccess =>
            val status = success.description.contains("on") match {
              case true => "on"
              case false => "off"
            }
            ResponseData(Results.Ok, JsObject(Seq("MESSAGE" -> JsString(status))))
          case failure =>
            PowerHelper.onFailure(asset,
              "Failed to get power status: %s".format(failure.description), user, PowerState)
        }
      }.getOrElse(Api.getErrorMessage("Invalid asset tag specified", Results.NotFound))
    }.getOrElse(Api.getErrorMessage("PowerManagement plugin not enabled"))
    formatResponseData(response)
  }}(Permissions.AssetManagementApi.PowerStatus)

  def powerManagement(tag: String) = Authenticated { user => Action { implicit req =>
    val errMsg = "Power management action must be one of: powerOff, powerOn, powerSoft, rebootSoft, rebootHard"
    val response = Form(of("action" -> of[PowerAction])).bindFromRequest.fold(
      err => Api.getErrorMessage(errMsg),
      action => PowerManagement.pluginEnabled { plugin =>
        Asset.findByTag(tag).map { asset =>
          PowerHelper.hasPermissions(asset, action) match {
            case Some(msg) =>
              Api.getErrorMessage(msg)
            case None =>
              val future = action match {
                case PowerOff => plugin.powerOff(asset)
                case PowerOn => plugin.powerOn(asset)
                case PowerSoft => plugin.powerSoft(asset)
                case RebootSoft => plugin.rebootSoft(asset)
                case RebootHard => plugin.rebootHard(asset)
                case PowerState => plugin.powerState(asset)
              }
              future() match {
                case success if success.isSuccess =>
                  PowerHelper.onSuccess(asset, "Successful power event: %s".format(action.toString),
                    user, action)
                case failure =>
                  PowerHelper.onFailure(
                    asset,
                    "Failed power event %s: %s".format(action.toString, failure.description),
                    user, action)
              }
          }
        }.getOrElse(Api.getErrorMessage("Invalid asset tag specified", Results.NotFound))
      }.getOrElse(Api.getErrorMessage("PowerManagement plugin not enabled"))
    )
    formatResponseData(response)
  }}(Permissions.AssetManagementApi.PowerManagement)

}
