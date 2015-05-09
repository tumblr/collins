package collins.controllers

import collins.controllers.actions.asset.GetProvisioningProfilesAction
import collins.controllers.actions.asset.PowerManagementAction
import collins.controllers.actions.asset.PowerStatusAction
import collins.controllers.actions.asset.ProvisionAction

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
