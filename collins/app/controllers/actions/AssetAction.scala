package controllers
package actions

import models.{Asset, AssetType}
import util.Feature

import java.util.concurrent.atomic.AtomicReference

// Helpers for actions
trait AssetAction {
  this: SecureAction =>

  protected val _asset = new AtomicReference[Option[Asset]](None)

  val AssetMessages = Asset.Messages

  def assetExists(t: String): Boolean = assetFromTag(t).isDefined

  def assetFromTag(t: String): Option[Asset] = Asset.findByTag(t)

  def setAsset(a: Option[Asset]): Unit = _asset.set(a)
  def getAsset(): Option[Asset] = _asset.get
  def definedAsset(): Asset = getAsset().get

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

  def assetIntakeAllowed(asset: Asset): Option[String] = {
    if (!asset.isNew)
      Some(AssetMessages.intakeError("new", asset))
    else if (asset.asset_type != AssetType.Enum.ServerNode.id)
      Some(AssetMessages.intakeError("type", asset))
    else if (!Feature("intakeSupported").toBoolean(true))
      Some(AssetMessages.intakeError("disabled", asset))
    else if (!Permissions.please(user(), Permissions.Resources.Intake))
      Some(AssetMessages.intakeError("permissions", asset))
    else
      None
  }

}
