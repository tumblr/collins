package collins.controllers.actions.ipaddress

import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.Asset
import collins.models.IpAddresses
import collins.util.security.SecuritySpecification

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
