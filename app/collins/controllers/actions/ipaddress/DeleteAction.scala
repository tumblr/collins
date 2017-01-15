package collins.controllers.actions.ipaddress

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.tuple
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
) extends SecureAction(spec, handler) with AssetAction with ParamValidation with AddressActionHelper {

  case class ActionDataHolder(asset: Asset, pool: Option[String], address: Option[String]) extends RequestDataHolder

  val dataForm = Form(tuple(
    "pool" -> validatedOptionalText(1),
    "address" -> validatedOptionalText(1)
  ))

  override def validate(): Validation = withValidAsset(assetTag) { asset =>
    val (pool, address): Tuple2[Option[String], Option[String]] = dataForm.bindFromRequest()(request).get
    address match {
      case Some(ip: String) => withValidAddress(ip) { validAddress => 
        Right(ActionDataHolder(asset, pool, address))
      }
      case None => Right(ActionDataHolder(asset, pool, None))
    }
  }

  override def execute(rd: RequestDataHolder) = Future {
    rd match {
      case ActionDataHolder(asset, pool, address) =>
        val deleted = address match {
          case Some(ip: String) => IpAddresses.deleteByAssetAndAddress(asset, ip)
          case None => IpAddresses.deleteByAssetAndPool(asset, pool)
        }
        tattler.notice("Deleted %d IP addresses".format(deleted), asset)
        ResponseData(Status.Ok, JsObject(Seq("DELETED" -> JsNumber(deleted))))
    }
  }
}
