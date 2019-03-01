package collins.controllers.actions.ipaddress

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms
import play.api.libs.concurrent.Execution.Implicits.defaultContext
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
import collins.util.security.SecuritySpecification

// Delete addressed for an asset, optionally by pool
case class DeleteAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with ParamValidation {

  case class ActionDataHolder(asset: Asset, pool: Option[String], address: Option[String]) extends RequestDataHolder

  val dataForm = Form(
    Forms.tuple(
      "pool" -> validatedOptionalText(1),
      "address" -> validatedOptionalText(1)
    )
  )

  override def validate(): Validation = withValidAsset(assetTag) { asset =>
    val (pool, address) : (Option[String], Option[String]) = dataForm.bindFromRequest()(request).fold(
      err => (None, None),
      str => str
    )
    Right(ActionDataHolder(asset, pool, address))
  }

  override def execute(rd: RequestDataHolder) = Future {
    val deleted = rd match {
      case ActionDataHolder(asset, None, None) => 
        Right(asset, IpAddresses.deleteByAssetAndPool(asset, None))
      case ActionDataHolder(asset, None, Some(address)) => 
        Right(asset, IpAddresses.deleteByAssetAndAddress(asset, Some(address)))
      case ActionDataHolder(asset, Some(pool), None) => 
        Right(asset, IpAddresses.deleteByAssetAndPool(asset, Some(pool)))
      case ActionDataHolder(asset, Some(pool), Some(address)) => 
        Left(RequestDataHolder.error400("both pool and address can not be set"))
    }

    deleted match {
      case Right((asset, num)) =>
        tattler.notice("Deleted %d IP addresses".format(num), asset)
        ResponseData(Status.Ok, JsObject(Seq("DELETED" -> JsNumber(num))))
      case Left(err) => handleError(err)
    }
  }
}
