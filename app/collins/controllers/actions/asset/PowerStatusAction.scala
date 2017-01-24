package collins.controllers.actions.asset

import play.api.libs.json.JsObject
import play.api.libs.json.JsString

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.models.Asset
import collins.power.PowerAction
import collins.power.PowerState
import collins.shell.CommandResult
import collins.util.config.AppConfig
import collins.util.security.SecuritySpecification

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
    val result = s.stdout
    val status = "error"
    if (result.contains("Chassis Power is on")) {
      val status = "on"
    } else if (result.contains("Chassis Power is off")) {
      val status = "off"
    }
    ResponseData(Status.Ok, JsObject(Seq("MESSAGE" -> JsString(status))))
  }
}
