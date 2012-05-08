package controllers

import actions.asset.{PowerManagementAction, PowerStatusAction}

trait AssetManagementApi {
  this: Api with SecureController =>

  def powerStatus(tag: String) =
    new PowerStatusAction(tag, Permissions.AssetManagementApi.PowerStatus, this)

  def powerManagement(tag: String) =
    new PowerManagementAction(tag, Permissions.AssetManagementApi.PowerManagement, this)

}
