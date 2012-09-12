package models.asset

import conversions._
import play.api.libs.json._

abstract class RemoteAssetProxy(jsonAsset: JsValue) extends RemoteAsset {

  private[this] val asset = Json.fromJson[AssetView](jsonAsset)

  def id = asset.id
  def tag = asset.tag
  def state = asset.state
  def status = asset.status
  def asset_type = asset.asset_type
  def created = asset.created
  def updated = asset.updated
  def deleted = asset.deleted

  def toJsValue() = {
    AssetFormat.writes(asset) ++ JsObject(Seq("LOCATION" -> JsString(hostTag)))
  }
}
