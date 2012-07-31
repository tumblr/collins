package controllers
package actions
package ipaddress

import models.{Asset, IpAddresses}
import util.SecuritySpecification

// Get the addresses associated with an asset
case class FindByAssetAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with AddressActionHelper {

  case class ActionDataHolder(asset: Asset) extends RequestDataHolder

  override def validate(): Validation = withValidAsset(assetTag) { asset =>
    Right(ActionDataHolder(asset))
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(asset) =>
      val addresses = IpAddresses.findAllByAsset(asset).toJson
      ResponseData(Status.Ok, addresses)
  }
}
