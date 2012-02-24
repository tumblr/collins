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

  def powerManagement(tag: String) = Authenticated { user => Action { implicit req =>
    def onSuccess(asset: Asset, msg: String) = {
      UserTattler.notice(asset, user, msg)
      Api.statusResponse(true)
    }
    def onFailure(asset: Asset, msg: String) = {
      UserTattler.warning(asset, user, msg)
      Api.getErrorMessage(msg)
    }
    def hasPermissions(asset: Asset, action: PowerAction): Option[String] = {
      action match {
        case PowerOff => if (AStatus.Enum(asset.status) != AStatus.Enum.Cancelled) {
          Some("Unable to Power Off when asset is not cancelled")
        } else {
          None
        }
        case _ => None
      }
    }
    val errMsg = "Power management action must be one of: off, on, rebootSoft, rebootHard"
    val response = Form(of("action" -> of[PowerAction])).bindFromRequest.fold(
      err => Api.getErrorMessage(errMsg),
      action => PowerManagement.pluginEnabled { plugin =>
        Asset.findByTag(tag).map { asset =>
          hasPermissions(asset, action) match {
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
                  onSuccess(asset, "Successful power event: %s".format(action.toString))
                case failure =>
                  onFailure(asset, "Failed power event %s: %s".format(action.toString, failure.description))
              }
          }
        }.getOrElse(Api.getErrorMessage("Invalid asset tag specified", Results.NotFound))
      }.getOrElse(Api.getErrorMessage("PowerManagement plugin not enabled"))
    )
    formatResponseData(response)
  }}(SecuritySpec(true, "infra"))

}
