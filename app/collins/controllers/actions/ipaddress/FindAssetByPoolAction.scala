package collins.controllers.actions.ipaddress

import scala.concurrent.Future

import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.Asset
import collins.models.IpAddresses
import collins.util.security.SecuritySpecification

// Find all assets in a pool
case class FindAssetsByPoolAction(
  pool: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AddressActionHelper {

  case class ActionDataHolder(cleanPool: String) extends RequestDataHolder

  override def validate(): Validation =
    Right(ActionDataHolder(convertPoolName(pool)))

  override def execute(rd: RequestDataHolder) = Future {
    rd match {
      case ActionDataHolder(cleanPool) =>
        IpAddresses.findInPool(cleanPool) match {
          case Nil =>
            handleError(
              RequestDataHolder.error404("No such pool or no assets in pool")
            )
          case list =>
            val jsList = list.map(e => Asset.findById(e.assetId).get.toJsValue).toList
            ResponseData(Status.Ok, JsObject(Seq("ASSETS" -> JsArray(jsList))))
        }
    }
  }
}
