package controllers
package actions

import models.{Asset, AssetType}
import util.Feature

import java.util.concurrent.atomic.AtomicReference

// Helpers for actions
trait AssetAction {
  this: SecureAction =>

  type Validation = Either[RequestDataHolder,RequestDataHolder]
  protected val _asset = new AtomicReference[Option[Asset]](None)

  val AssetMessages = Asset.Messages

  def assetExists(t: String): Boolean = assetFromTag(t).isDefined

  def assetFromTag(t: String): Option[Asset] = Asset.findByTag(t)

  def setAsset(a: Option[Asset]): Unit = _asset.set(a)
  def getAsset(): Option[Asset] = _asset.get
  def definedAsset(): Asset = getAsset().get

  def assetNotFound(t: String) = RequestDataHolder.error404(AssetMessages.notFound(t))

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

  def assetIntakeAllowed(asset: Asset): Boolean = {
    val isNew = asset.isNew
    val rightType = asset.asset_type == AssetType.Enum.ServerNode.id
    val intakeSupported = Feature("intakeSupported").toBoolean(true)
    val rightRole = Permissions.please(user(), Permissions.Resources.Intake)
    intakeSupported && isNew && rightType && rightRole
  }

}
