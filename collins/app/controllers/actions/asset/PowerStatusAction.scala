package controllers
package actions
package asset

import models.Asset
import util.SecuritySpecification

import play.api.libs.json._

import com.tumblr.play.{CommandResult, PowerAction, PowerState}

case class PowerStatusAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends PowerManagementActionHelper(assetTag, spec, handler) {

  override val powerAction = Some(PowerState)

  override protected def ignoreAsset(asset: Asset): Boolean = false
  override protected def actionAllowed(asset: Asset, pa: PowerAction): Boolean = true

  override protected def onSuccess(s: CommandResult): ResponseData = {
    logSuccessfulPowerEvent()
    val status = s.output.contains("on") match {
      case true => "on"
      case false => "off"
    }
    ResponseData(Status.Ok, JsObject(Seq("MESSAGE" -> JsString(status))))
  }
}
