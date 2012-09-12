package controllers
package actions
package ipaddress

import models.{Asset, IpAddresses}
import util.security.SecuritySpecification

import play.api.libs.json._

// Find all assets in a pool
case class FindAssetsByPoolAction(
  pool: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AddressActionHelper {

  case class ActionDataHolder(cleanPool: String) extends RequestDataHolder

  override def validate(): Validation =
    Right(ActionDataHolder(convertPoolName(pool)))

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(cleanPool) =>
      IpAddresses.findInPool(cleanPool) match {
        case Nil =>
          handleError(
            RequestDataHolder.error404("No such pool or no assets in pool")
          )
        case list =>
          val jsList = list.map(e => Asset.findById(e.asset_id).get.toJsValue).toList
          ResponseData(Status.Ok, JsObject(Seq("ASSETS" -> JsArray(jsList))))
      }
  }
}
