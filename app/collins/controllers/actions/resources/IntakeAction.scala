package collins.controllers.actions.resources

import collins.controllers.actions.AssetAction
import collins.controllers.actions.EphemeralDataHolder
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.AssetMeta.Enum.ChassisTag
import collins.util.MessageHelperI
import collins.util.config.Feature
import collins.validation.StringUtil

trait IntakeAction extends AssetAction with MessageHelperI {
  this: SecureAction =>

  val assetId: Long

  override val parentKey = "asset.intake.error"

  val Stage1Template = views.html.resources.intake
  val Stage2Template = views.html.resources.intake2
  val Stage3Template = views.html.resources.intake3

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(assetId) { asset =>
      assetIntakeAllowed(asset) match {
        case None =>
          Right(EphemeralDataHolder())
        case Some(err) =>
          Left(RequestDataHolder.error400(err))
      }
    }
  }

  protected def cleanString(input: String) = StringUtil.trim(input)

  protected def verifyChassisTag(tag: String): Either[RequestDataHolder,String] = cleanString(tag) match {
    case None => Left(RequestDataHolder.error400(chassisInvalidMessage))
    case Some(chassisTag) =>
      definedAsset.getMetaAttribute(ChassisTag.toString, 10) match {
        case Nil =>
          Feature.intakeChassisTagOptional match {
            case true => Right(tag)
            case false => Left(RequestDataHolder.error400(chassisNotFoundMessage))
          }
        case head :: Nil if head.getValue != chassisTag =>
          Left(RequestDataHolder.error400(chassisMismatchMessage(head.getValue, chassisTag)))
        case head :: Nil =>
          Right(chassisTag)
        case head :: tail =>
          Left(RequestDataHolder.error400(chassisMultipleMessage))
      }
    }
  protected def chassisInvalidMessage =
    messageWithDefault("chassisInvalid", "Invalid chassis tag specified")
  protected def chassisMismatchMessage(expectedTag: String, actualTag: String) =
    messageWithDefault("chassisMismatch", "Chassis tags do not match",
      definedAsset.tag, expectedTag, actualTag)
  protected def chassisMultipleMessage =
    messageWithDefault("chassisMultiple", "Asset has multiple chassis tags", definedAsset.tag)
  protected def chassisNotFoundMessage =
    messageWithDefault("chassisNotFound", "No chassis tag found for asset", definedAsset.tag)
}
