package controllers

import com.tumblr.play.{CommandResult, ProvisionerProfile, RebootType}

import models._
import util._

import play.api.mvc._
import play.api.libs.json._
import play.api.data._

trait AssetWebApi {
  this: Api with SecureController =>

  // POST /asset/:tag/cancel
  def cancelAsset(tag: String) = Authenticated { user => Action { implicit req =>
    val asset = Asset.findByTag(tag)
    AsyncResult {
      BackgroundProcessor.send(AssetCancelProcessor(tag)) { case(ex,res) =>
        val rd: ResponseData = ex.map { err =>
          Api.getErrorMessage(err.getMessage)
        }.orElse{
          res.get match {
            case Left(err) => Some(err)
            case Right(success) =>
              UserTattler.notice(asset.get, user, "Server cancelled")
              Some(ResponseData(Results.Ok, JsObject(Seq("SUCCESS" -> JsNumber(success)))))
          }
        }.get
        formatResponseData(rd)
      }
    }
  }}(SecuritySpec(true, "infra"))

  def rebootAsset(tag: String) = Authenticated { user => Action { implicit req =>
    def onSuccess(asset: Asset, msg: String) = {
      UserTattler.notice(asset, user, msg)
      Api.statusResponse(true)
    }
    def onFailure(asset: Asset, msg: String) = {
      UserTattler.warning(asset, user, msg)
      Api.getErrorMessage(msg)
    }
    val errMsg = "Reboot type (soft/hard) must be specified"
    val msg = Form(of("type" -> text)).bindFromRequest.fold(
      err => {
        Api.getErrorMessage(errMsg)
      },
      suc => suc match {
        case RebootType(rebootType) =>
          Asset.findByTag(tag).map { asset =>
            SoftLayer.pluginEnabled.map { plugin =>
              plugin.softLayerId(asset).map { id =>
                plugin.rebootServer(id, rebootType)() match {
                  case true =>
                    onSuccess(asset, "Successfull reboot")
                  case false =>
                    onFailure(asset, "Unable to do %s reboot".format(rebootType))
                }
              }.getOrElse {
                Api.getErrorMessage("Asset with tag %s is not a softlayer asset".format(asset.tag))
              }
            }.getOrElse {
              Api.getErrorMessage("SoftLayer plugin not enabled")
            }
          }.getOrElse {
            Api.getErrorMessage("Invalid Asset tag specified")
          }
        case _ =>
          Api.getErrorMessage(errMsg)
      })
    formatResponseData(msg)
  }}(SecuritySpec(true, "infra"))

  // POST /asset/:tag/provision
  def provisionAsset(tag: String) = Authenticated { user => Action { implicit req =>
    def onSuccess(asset: Asset, profile: ProvisionerProfile) {
      val id = profile.identifier
      val label = profile.label
      UserTattler.note(asset, user, "Provisioned as %s".format(label))
      Model.withTransaction { implicit con =>
        MetaWrapper.createMeta(asset, Map("NODECLASS" -> id))
      }
    }
    def onFailure(asset: Asset, role: String, cmd: CommandResult) {
      val msg = "Provisioning as %s failed - %s".format(cmd.toString)
      UserTattler.warning(asset, user, msg)
    }
    Asset.findByTag(tag).map { asset =>
      Form(of(
        "role" -> text,
        "contact" -> text(3),
        "suffix" -> optional(text(3))
      )).bindFromRequest.fold(
        err => {
          formatResponseData(Api.getErrorMessage("contact and role must be specified"))
        },
        suc => {
          Provisioner.pluginEnabled { plugin =>
            val (role, contact, suffix) = suc
            plugin.makeRequest(asset.tag, role, Some(contact), suffix).map { request =>
              AsyncResult {
                BackgroundProcessor.send(ProvisionerProcessor(request)) { res =>
                  val reply = res match {
                    case (Some(error), _) =>
                      onFailure(asset,
                        request.profile.label,
                        CommandResult(-100, "Error: %s".format(error.getMessage))
                      )
                      Api.getErrorMessage(
                        "There was an exception processing your request: %s".format(error.getMessage),
                        Results.InternalServerError,
                        Some(error)
                      )
                    case (_, opt) =>
                      val success = opt.getOrElse(CommandResult(-99, "No result data"))
                      if (success.exitCode != 0) {
                        onFailure(asset, request.profile.label, success)
                        val msg = "There was an error processing your request. Exit Code %d\n%s".format(success.exitCode, success.output)
                        Api.getErrorMessage(msg, Results.InternalServerError, None)
                      } else {
                        onSuccess(asset, request.profile)
                        ResponseData(Results.Ok, JsObject(Seq("SUCCESS" -> JsNumber(0))))
                      }
                  }
                  formatResponseData(reply)
                }
              }
            }.getOrElse {
              formatResponseData(Api.getErrorMessage("Invalid profile specified"))
            }
          }.getOrElse {
            formatResponseData(Api.getErrorMessage("Provisioner plugin not enabled"))
          }
        }
      )
    }.getOrElse {
      formatResponseData(Api.getErrorMessage("Specified asset tag is invalid"))
    }
  }}(SecuritySpec(true, "infra"))

}
