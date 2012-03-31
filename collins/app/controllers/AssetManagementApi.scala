package controllers

import forms._
import models.{Status => AStatus}
import models._
import util._

import play.api.mvc._
import play.api.libs.json._
import play.api.data._

import com.tumblr.play.{PowerAction, PowerOff, PowerOn, PowerCycle, RebootSoft, RebootHard}

trait AssetManagementApi {
  this: Api with SecureController =>

  object PowerHelper {
    def onSuccess(asset: Asset, msg: String, user: Option[User]) = {
      UserTattler.notice(asset, user, msg)
      Api.statusResponse(true)
    }
    def onFailure(asset: Asset, msg: String, user: Option[User]) = {
      UserTattler.warning(asset, user, msg)
      Api.getErrorMessage(msg)
    }
    def hasPermissions(asset: Asset, action: PowerAction): Option[String] = {
      if (AppConfig.ignoreAsset(asset)) {
        return Some("Specified asset has been configured to not allow this operation")
      }
      action match {
        case PowerOff => if (AStatus.Enum(asset.status) != AStatus.Enum.Cancelled) {
          Some("Unable to Power Off when asset is not cancelled")
        } else {
          None
        }
        case _ => None
      }
    }
  }

  def powerStatus(tag: String) = Authenticated { user => Action { implicit req =>
    val response = PowerManagement.pluginEnabled { plugin =>
      Asset.findByTag(tag).map { asset =>
        plugin.powerState(asset)() match {
          case success if success.isSuccess =>
            ResponseData(Results.Ok, JsObject(Seq("MESSAGE" -> JsString(success.description))))
          case failure =>
            PowerHelper.onFailure(asset,
              "Failed to get power status: %s".format(failure.description), user)
        }
      }.getOrElse(Api.getErrorMessage("Invalid asset tag specified", Results.NotFound))
    }.getOrElse(Api.getErrorMessage("PowerManagement plugin not enabled"))
    formatResponseData(response)
  }}(SecuritySpec(true, Nil))

  def powerManagement(tag: String) = Authenticated { user => Action { implicit req =>
    val errMsg = "Power management action must be one of: powerOff, powerOn, powerCycle, rebootSoft, rebootHard"
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
                case PowerCycle => plugin.powerCycle(asset)
                case RebootSoft => plugin.rebootSoft(asset)
                case RebootHard => plugin.rebootHard(asset)
              }
              future() match {
                case success if success.isSuccess =>
                  PowerHelper.onSuccess(asset, "Successful power event: %s".format(action.toString),
                    user)
                case failure =>
                  PowerHelper.onFailure(
                    asset,
                    "Failed power event %s: %s".format(action.toString, failure.description),
                    user)
              }
          }
        }.getOrElse(Api.getErrorMessage("Invalid asset tag specified", Results.NotFound))
      }.getOrElse(Api.getErrorMessage("PowerManagement plugin not enabled"))
    )
    formatResponseData(response)
  }}(SecuritySpec(true, "infra"))

}
