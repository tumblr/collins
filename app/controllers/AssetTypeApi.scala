package controllers

import actions.assettype.{CreateAction, DeleteAction, GetAction, UpdateAction}

trait AssetTypeApi {
  this: Api with SecureController =>

  // GET /api/assettype/:name
  def getAssetType(name: String) = GetAction(Some(name), Permissions.AssetTypeApi.GetAssetTypes, this)

  // GET /api/assettypes
 def getAssetTypes() = GetAction(None, Permissions.AssetTypeApi.GetAssetTypes, this)

  // PUT /api/assettype/:tag
  def createAssetType(name: String) = CreateAction(name, Permissions.AssetTypeApi.CreateAssetType, this)

  // POST /api/assettype/:tag
  def updateAssetType(name: String) = UpdateAction(name, Permissions.AssetTypeApi.UpdateAssetType, this)

  // DELETE /api/assettype/:tag
  def deleteAssetType(name: String) = DeleteAction(name, Permissions.AssetTypeApi.DeleteAssetType, this)

}
