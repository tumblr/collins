package collins.models.asset

import play.api.libs.json.JsObject

trait RemoteAsset extends AssetView {
  val json: JsObject
  val hostTag: String //the asset representing the data center this asset belongs to
  val remoteUrl: String

  def remoteHost = Some(remoteUrl)
}
