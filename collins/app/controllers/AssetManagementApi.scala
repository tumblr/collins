package controllers

import actions.asset.{
  PowerManagementAction, PowerStatusAction, ProvisionAction, GetProvisioningProfilesAction
}

trait AssetManagementApi {
  this: Api with SecureController =>

  def powerStatus(tag: String) =
    new PowerStatusAction(tag, Permissions.AssetManagementApi.PowerStatus, this)

  def powerManagement(tag: String) =
    new PowerManagementAction(tag, Permissions.AssetManagementApi.PowerManagement, this)

  // POST /asset/:tag/provision
  def provisionAsset(tag: String) =
    ProvisionAction(tag, Permissions.AssetManagementApi.ProvisionAsset, this)

  def getProvisioningProfiles =
    GetProvisioningProfilesAction(Permissions.AssetManagementApi.GetProvisioningProfiles, this)

}
