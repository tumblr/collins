package controllers

import actions.state.{CreateAction, DeleteAction, GetAction}

trait AssetStateApi {
  this: Api with SecureController =>

  def createState(name: String) =
    CreateAction(name, Permissions.AssetStateApi.Create, this)

  def deleteState(name: String) =
    DeleteAction(name, Permissions.AssetStateApi.Delete, this)

  def getState(name: String) =
    GetAction(name, Permissions.AssetStateApi.Get, this)
}
