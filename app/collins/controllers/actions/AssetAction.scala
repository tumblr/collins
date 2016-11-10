package collins.controllers.actions

import java.util.concurrent.atomic.AtomicReference

import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.mvc.Result

import collins.controllers.Api
import collins.controllers.Permissions
import collins.controllers.ResponseData
import collins.models.Asset
import collins.models.asset.AssetView
import collins.models.asset.RemoteAsset
import collins.models.shared.Page
import collins.util.config.Feature

// Helpers for actions
trait AssetAction {
  this: SecureAction =>

  protected val _asset = new AtomicReference[Option[Asset]](None)

  val AssetMessages = Asset.Messages

  def assetExists(t: String): Boolean = assetFromTag(t).isDefined

  def assetFromTag(t: String): Option[Asset] = Asset.findByTag(t)

  def setAsset(a: Option[Asset]): Unit = _asset.set(a)
  def setAsset(a: Asset): Unit = _asset.set(Some(a))
  def getAsset(): Option[Asset] = _asset.get
  def definedAsset: Asset = getAsset().get

  def assetNotFound(t: String) = RequestDataHolder.error404(AssetMessages.notFound(t))

  def withValidAsset(id: Long)(f: Asset => Validation): Validation = Asset.findById(id) match {
    case None => Left(RequestDataHolder.error404(AssetMessages.invalidId(id)))
    case Some(asset) => withValidAsset(asset.tag)(f)
  }

  def withValidAsset(t: String)(f: Asset => Validation): Validation = Asset.isValidTag(t) match {
    case true =>
      assetFromTag(t) match {
        case None => Left(assetNotFound(t))
        case asset =>
          setAsset(asset)
          f(asset.get)
      }
    case false =>
      Left(RequestDataHolder.error400(AssetMessages.invalidTag(t)))
  }

  def assetIntakeAllowed[T <: AssetView](asset: T): Option[String] = {
    if (!asset.isNew)
      Some(AssetMessages.intakeError("new", asset))
    else if (!asset.isServerNode)
      Some(AssetMessages.intakeError("type", asset))
    else if (!Feature.intakeSupported)
      Some(AssetMessages.intakeError("disabled", asset))
    else if (!Permissions.please(user(), Permissions.Resources.Intake))
      Some(AssetMessages.intakeError("permissions", asset))
    else
      None
  }

}

/**
 * Common functionality between find and similar actions, might be able to merge with above
 */
trait AssetResultsAction {
  this: SecureAction =>

  protected def handleSuccess(p: Page[AssetView], details: Boolean) = isHtml match {
    case true =>
      handleWebSuccess(p, details)
    case false =>
      handleApiSuccess(p, details)
  }

  protected def handleWebSuccess(p: Page[AssetView], details: Boolean): Result = {
    Api.errorResponse(NotImplementedError.toString, NotImplementedError.status().get)
  }

  protected def handleApiSuccess(p: Page[AssetView], details: Boolean): Result = {
    val items = p.items.map {
      case a: Asset => if (details){
        a.getAllAttributes.exposeCredentials(user.canSeeEncryptedTags).toJsValue
      } else {
        a.toJsValue
      }
      case v: RemoteAsset => v.toJsValue
    }.toList
    ResponseData(Status.Ok, JsObject(p.getPaginationJsObject() ++ Seq(
      "Data" -> JsArray(items)
    )), p.getPaginationHeaders)
  }
}
