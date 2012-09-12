package controllers
package actions
package asset

import models.Asset
import util.config.AppConfig
import util.security.SecuritySpecification

import play.api.libs.json._

import collins.power.{PowerAction, PowerState}
import collins.shell.CommandResult

case class PowerStatusAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends PowerManagementActionHelper(assetTag, spec, handler) {

  override val powerAction = Some(PowerState)

  override protected def ignoreAsset(asset: Asset): Boolean = false
  override protected def actionAllowed(asset: Asset, pa: PowerAction): Boolean = true
  override protected def assetStateAllowed(asset: Asset): Boolean = true

  override protected def onNoResult(): ResponseData = if (AppConfig.isDev) {
     ResponseData(Status.Ok, JsObject(Seq("MESSAGE" -> JsString("on - fake for dev"))))
  } else {
    super.onNoResult()
  }
  override protected def onSuccess(s: CommandResult): ResponseData = {
    logSuccessfulPowerEvent()
    val status = s.stdout.contains("on") match {
      case true => "on"
      case false => "off"
    }
    ResponseData(Status.Ok, JsObject(Seq("MESSAGE" -> JsString(status))))
  }
}
