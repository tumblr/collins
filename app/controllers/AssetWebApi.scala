package controllers

import actors._
import collins.provisioning.{ProvisionerProfile, ProvisionerRequest}
import collins.shell.CommandResult
import forms._
import models.{Status => AStatus}
import models._
import util._
import util.config.AppConfig
import util.concurrent.BackgroundProcessor

import play.api.mvc._
import play.api.libs.json._
import play.api.data._
import play.api.data.Forms._

trait AssetWebApi {
  this: Api with SecureController =>

  // POST /asset/:tag/cancel
  def cancelAsset(tag: String) = Authenticated { user => Action { implicit req =>
    if (AppConfig.ignoreAsset(tag)) {
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
  }}(Permissions.AssetWebApi.CancelAsset)

}
