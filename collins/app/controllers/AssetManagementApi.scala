package controllers

import forms._
import models.{Status => AStatus}
import models._
import util._
import util.concurrent.BackgroundProcessor
import util.plugins.IpmiPowerCommand

import play.api.mvc._
import play.api.libs.json._
import play.api.data._

import com.tumblr.play.{PowerAction, PowerOff, PowerOn, PowerSoft, PowerState, RebootSoft}
import com.tumblr.play.{RebootHard, Identify, Verify}
import com.tumblr.play.{PowerManagement => PowerManagementPlugin}

trait AssetManagementApi {
  this: Api with SecureController =>

  object PowerHelper {
    def onSuccess(asset: Asset, msg: String, user: Option[User], action: PowerAction): ResponseData = {
      if (action == PowerState) {
        UserTattler.notice(asset, user, msg)
      } else {
        UserTattler.warning(asset, user, msg)
      }
      Api.statusResponse(true)
    }
    def onFailure(asset: Asset, p: PowerManagementPlugin, msg: String, user: Option[User], action: PowerAction): ResponseData = {
      val nmsg = if (action != Verify) {
        p.verify(asset)() match {
          case success if success.isSuccess =>
            "%s, and is reachable on IPMI address".format(msg)
          case failure if !failure.isSuccess =>
            "%s, and is NOT reachable on IPMI address".format(msg)
        }
      } else {
        msg
      }
      if (action == PowerState) {
        UserTattler.notice(asset, user, nmsg)
      } else {
        UserTattler.warning(asset, user, nmsg)
      }
      Api.getErrorMessage(nmsg)
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
            PowerHelper.onFailure(asset, plugin,
              "Failed to get power status: %s".format(failure.description), user, PowerState)
        }
      }.getOrElse(Api.getErrorMessage("Invalid asset tag specified", Results.NotFound))
    }.getOrElse(Api.getErrorMessage("PowerManagement plugin not enabled"))
    formatResponseData(response)
  }}(Permissions.AssetManagementApi.PowerStatus)

  def powerManagement(tag: String) = Authenticated { user => Action { implicit req =>
    val errMsg = "Power management action must be one of: powerOff, powerOn, powerSoft, rebootSoft, rebootHard"
    val assetOption = Asset.findByTag(tag)
    if (!assetOption.isDefined) {
      formatResponseData(Api.getErrorMessage("Asset with tag %s does not exist".format(tag), Results.NotFound))
    } else if (!PowerManagement.pluginEnabled.isDefined) {
      formatResponseData(Api.getErrorMessage("PowerManagement plugin not enabled"))
    } else {
      val asset = assetOption.get
      val plugin = PowerManagement.pluginEnabled.get
      Form(of(
        "action" -> of[PowerAction]
      )).bindFromRequest.fold(
        err => formatResponseData(Api.getErrorMessage(errMsg)),
        action => PowerHelper.hasPermissions(asset, action) match {
          case Some(msg) =>
            formatResponseData(Api.getErrorMessage(msg))
          case None => AsyncResult {
            val cmd = IpmiPowerCommand.fromPowerAction(asset, action)
            BackgroundProcessor.send(cmd) { result =>
              val res = IpmiCommand.fromResult(result) match {
                case Left(throwable) =>
                  PowerHelper.onFailure(asset, plugin,
                    "Failed power event %s: %s".format(action.toString, throwable.toString),
                    user, action
                  )
                case Right(None) =>
                  PowerHelper.onSuccess(asset, "Fake successful power event: %s".format(action.toString),
                    user, action)
                case Right(Some(suc)) if suc.isSuccess =>
                  PowerHelper.onSuccess(asset, "Successful power event: %s".format(action.toString),
                    user, action)
                case Right(Some(fail)) if !fail.isSuccess =>
                  PowerHelper.onFailure(asset, plugin,
                    "Failed power event %s: %s".format(action.toString, fail.toString), user, action)
              }
              formatResponseData(res)
            }
          }
        }
      )
    }
  }}(Permissions.AssetManagementApi.PowerManagement)


}
