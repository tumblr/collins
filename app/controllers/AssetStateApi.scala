package controllers

import actions.state.CreateAction

trait AssetStateApi {
  this: Api with SecureController =>

  def createState(name: String) =
    CreateAction(name, Permissions.AssetStateApi.Create, this)
}
