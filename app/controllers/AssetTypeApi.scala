package controllers

import actions.assettype.{CreateAction, DeleteAction, GetAction}

trait AssetTypeApi {
  this: Api with SecureController =>

  // GET /api/assettype/:name
  def getAssetType(name: String) = GetAction(Some(name), Permissions.AssetTypeApi.GetAssetTypes, this)

  // GET /api/assettypes
 def getAssetTypes() = GetAction(None, Permissions.AssetTypeApi.GetAssetTypes, this)

  // PUT /api/assettype/:tag
  def createAssetType(name: String) = CreateAction(name, Permissions.AssetTypeApi.CreateAssetType, this)

  // DELETE /api/assettype/:tag
  def deleteAssetType(name: String) = DeleteAction(name, Permissions.AssetTypeApi.DeleteAssetType, this)

}
