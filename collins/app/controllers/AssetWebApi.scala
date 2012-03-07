package controllers

import actors._
import com.tumblr.play.{CommandResult, ProvisionerProfile, ProvisionerRequest}
import forms._
import models._
import util._

import play.api.mvc._
import play.api.libs.json._
import play.api.data._

trait AssetWebApi {
  this: Api with SecureController =>

  // POST /asset/:tag/cancel
  def cancelAsset(tag: String) = Authenticated { user => Action { implicit req =>
    if (Helpers.ignoreDangerousCommand(tag)) {
      formatResponseData(
        Api.getErrorMessage("Specified asset has been configured to not permit this operation")
      )
    } else {
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
    }
  }}(SecuritySpec(true, "infra"))

  // POST /asset/:tag/provision
  type ProvisionForm = Tuple6[String,String,Option[String],Option[String],Option[String],Option[String]]
  def provisionAsset(tag: String) = Authenticated { user => Action { implicit req =>
    def onSuccess(asset: Asset, profile: ProvisionerProfile) {
      val label = profile.label
      val role = profile.role
      val attribs: Map[String,String] = Map() ++
        role.primary_role.map(s => Map("PRIMARY_ROLE" -> s)).getOrElse(Map("PRIMARY_ROLE" -> "")) ++
        role.secondary_role.map(s => Map("SECONDARY_ROLE" -> s)).getOrElse(Map("SECONDARY_ROLE" -> "")) ++
        role.pool.map(s => Map("POOL" -> s)).getOrElse(Map("POOL" -> "")) ++
        AssetStateMachine.DeleteSomeAttributes.map(s => (s -> "")).toMap;

      val newAsset = Asset.findById(asset.getId)
      if (attribs.nonEmpty) {
        AssetLifecycle.updateAssetAttributes(newAsset.get, attribs)
      }

      UserTattler.note(newAsset.get, user, "Provisioned as %s".format(label))
    }
    def onFailure(asset: Asset, profile: String, cmd: CommandResult) {
      val msg = "Provisioning as %s failed - %s".format(profile, cmd.toString)
      UserTattler.warning(asset, user, msg)
    }
    def validate(asset: Asset, form: ProvisionForm): Either[String,ProvisionerRequest] = {
      Provisioner.pluginEnabled { plugin =>
        val (profile, contact, suffix, primary_role, pool, secondary_role) = form
        val optrequest = plugin.makeRequest(asset.tag, profile, Some(contact), suffix)
        if (!optrequest.isDefined) {
          return Left("Invalid profile %s specified".format(profile))
        }
        val request = optrequest.get
        var role = request.profile.role
        if (!role.primary_role.isDefined && role.requires_primary_role) {
          if (primary_role.isDefined) {
            role = role.copy(primary_role = Some(primary_role.get.toUpperCase))
          } else {
            return Left("A primary_role is required but none was specified")
          }
        }
        if (!role.pool.isDefined && role.requires_pool) {
          if (pool.isDefined) {
            role = role.copy(pool = Some(pool.get.toUpperCase))
          } else {
            return Left("A pool is required but none was specified")
          }
        }
        if (!role.secondary_role.isDefined && role.requires_secondary_role) {
          if (secondary_role.isDefined) {
            role = role.copy(secondary_role = Some(secondary_role.get.toUpperCase))
          } else {
            return Left("A secondary_role is required but none was specified")
          }
        }
        val newProfile = request.profile.copy(role = role)
        Right(request.copy(profile = newProfile))
      }.getOrElse(Left("Provisioner plugin not enabled"));
    }
    def provision(asset: Asset, request: ProvisionerRequest) = AsyncResult {
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
    Asset.findByTag(tag).map { asset =>
      Form(of(
        "profile" -> text,
        "contact" -> text(3),
        "suffix" -> optional(text(3)),
        "primary_role" -> optional(text),
        "pool" -> optional(text),
        "secondary_role" -> optional(text)
      )).bindFromRequest.fold(
        err => {
          formatResponseData(Api.getErrorMessage("contact and profile must be specified"))
        },
        suc => {
          if (Helpers.ignoreDangerousCommand(asset)) {
            formatResponseData(Api.getErrorMessage(
              "Asset has been configured to ignore dangerous commands"
            ))
          } else {
            validate(asset, suc) match {
              case Left(err) => formatResponseData(Api.getErrorMessage(
                "Provisioning error: %s".format(err)
              ))
              case Right(request) => provision(asset, request)
            }
          }
        }
      )
    }.getOrElse {
      formatResponseData(Api.getErrorMessage("Specified asset tag is invalid"))
    }
  }}(SecuritySpec(true, "infra"))

}
