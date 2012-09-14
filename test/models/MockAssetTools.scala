package models

import asset.{AssetView, RemoteAsset}
import play.api.libs.json._

import java.sql.Timestamp
 
case class MockRemoteAsset( 
  tag: String, 
  hostTag: String = "localhost", 
  json: JsObject = JsObject(Seq.empty),  
  remoteUrl: String = "http://localhost",
  id: Long = 42L,
  state: Int = 0
) extends RemoteAsset { 

  import asset.conversions._
  def asset_type: Int = AssetType.Enum.ServerNode.id
  def status: Int = Status.Enum.Allocated.id
  def created: Timestamp = new Timestamp(System.currentTimeMillis) 
  def updated: Option[Timestamp] =  None 
  def deleted: Option[Timestamp] = None
 
  def getHostnameMetaValue(): Option[String] = None 
  def getPrimaryRoleMetaValue(): Option[String] = None 
  override def getStatusName(): String = "Allocated"
  override def toJsValue() = Json.toJson[AssetView](this)
} 


class MockRemoteAssetClient(val assets: Seq[AssetView], val tag: String = "mock") extends RemoteAssetClient {
  def getTotal = assets.size

  var numRequests: Int = 0

  def getRemoteAssets(params: AssetSearchParameters, page: PageParams) = {
    numRequests += 1
    val firstAssetIndex = page.page * page.size
    val lastAssetIndex = firstAssetIndex + page.size
    assets.slice(firstAssetIndex, lastAssetIndex)
  }
}   
    
object AssetGenerator {
    def apply(num: Int) = (0 to num - 1).map{i => new MockRemoteAsset("%09d".format(i), id = i.toLong)}
}
