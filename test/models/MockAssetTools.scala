package models

import play.api.libs.json._

import java.sql.Timestamp
 
case class MockRemoteAsset( 
  tag: String, 
  hostTag: String = "localhost", 
  json: JsObject = JsObject(Seq.empty),  
  remoteUrl: String = "http://localhost" 
) extends RemoteAsset { 
 
  def created: Timestamp = new Timestamp(System.currentTimeMillis) 
  def updated: Option[Timestamp] =  None 
 
  def getHostnameMetaValue(): Option[String] = None 
  def getPrimaryRoleMetaValue(): Option[String] = None 
  def getStatusName(): String = "" 
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
    def apply(num: Int) = (0 to num - 1).map{i => new MockRemoteAsset("%09d".format(i))}
}
