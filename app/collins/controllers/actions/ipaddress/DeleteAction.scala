package collins.controllers.actions.ipaddress

import play.api.data.Form
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.validators.ParamValidation
import collins.models.Asset
import collins.models.IpAddresses
import collins.util.ApiTattler
import collins.util.security.SecuritySpecification

// Delete addressed for an asset, optionally by pool
case class DeleteAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with ParamValidation {

  case class ActionDataHolder(asset: Asset, pool: Option[String]) extends RequestDataHolder

  val dataForm = Form(
    "pool" -> validatedOptionalText(1)
  )

  override def validate(): Validation = withValidAsset(assetTag) { asset =>
    val pool: Option[String] = dataForm.bindFromRequest()(request).fold(
      err => None,
      str => str
    )
    Right(ActionDataHolder(asset, pool))
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(asset, pool) =>
      val deleted = IpAddresses.deleteByAssetAndPool(asset, pool)
      ApiTattler.notice(asset, userOption, "Deleted %d IP addresses".format(deleted))
      ResponseData(Status.Ok, JsObject(Seq("DELETED" -> JsNumber(deleted))))
  }
}
