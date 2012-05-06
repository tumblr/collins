package controllers
package actions

import models.Asset

import java.util.concurrent.atomic.AtomicReference

// Helpers for actions
trait AssetAction {
  type Validation = Either[RequestDataHolder,RequestDataHolder]
  protected val _asset = new AtomicReference[Option[Asset]](None)

  val AssetMessages = Asset.Messages

  def assetExists(t: String): Boolean = assetFromTag(t).isDefined

  def assetFromTag(t: String): Option[Asset] = Asset.findByTag(t)

  def setAsset(a: Option[Asset]): Unit = _asset.set(a)
  def getAsset(): Option[Asset] = _asset.get
  def definedAsset(): Asset = getAsset().get

  def assetNotFound(t: String) = RequestDataHolder.error404(AssetMessages.notFound(t))

  def withValidAsset(t: String)(f: Asset => Validation): Validation = {
    assetFromTag(t) match {
      case None => Left(assetNotFound(t))
      case asset =>
        setAsset(asset)
        f(asset.get)
    }
  }
}
