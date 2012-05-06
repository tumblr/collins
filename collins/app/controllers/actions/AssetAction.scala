package controllers
package actions

import models.Asset

import java.util.concurrent.atomic.AtomicReference

// Helpers for actions
trait AssetAction {
  protected val _asset = new AtomicReference[Option[Asset]](None)

  val AssetMessages = Asset.Messages

  def assetExists(t: String): Boolean = assetFromTag(t).isDefined

  def assetFromTag(t: String): Option[Asset] = Asset.findByTag(t)

  def setAsset(a: Option[Asset]): Unit = _asset.set(a)
  def getAsset(): Option[Asset] = _asset.get
  def definedAsset(): Asset = getAsset().get
}
