package controllers

import actions.state.{CreateAction, DeleteAction, GetAction, UpdateAction}

trait AssetStateApi {
  this: Api with SecureController =>

  def createState(name: String) =
    CreateAction(name, Permissions.AssetStateApi.Create, this)

  def deleteState(name: String) =
    DeleteAction(name, Permissions.AssetStateApi.Delete, this)

  def getState(name: String) =
    GetAction(Some(name), Permissions.AssetStateApi.Get, this)

  def getStates() =
    GetAction(None, Permissions.AssetStateApi.Get, this)

  def updateState(name: String) =
    UpdateAction(name, Permissions.AssetStateApi.Update, this)
}
