package controllers

import actions.logs.{CreateAction, FindAction, SolrFindAction}

import models.PageParams

trait AssetLogApi {
  this: Api with SecureController =>

  // GET /assets/:tag/logs
  def getLogData(tag: String, page: Int, size: Int, sort: String, filter: String) =
    FindAction(Some(tag), PageParams(page, size, sort, "date"), filter, Permissions.AssetLogApi.Get, this)

  // GET /assets/logs
  def getAllLogData(page: Int, size: Int, sort: String, filter: String) =
    FindAction(None, PageParams(page, size, sort, "date"), filter, Permissions.AssetLogApi.GetAll, this)

  // GET /api/assets/logs/search
  def searchLogs(page: Int, size: Int, sortField: String, sort: String, query: String) = 
    SolrFindAction(query, PageParams(page, size, sort, sortField), Permissions.AssetLogApi.GetAll, this)

  // PUT /api/asset/:tag/log
  def submitLogData(tag: String) =
    CreateAction(tag, spec = Permissions.AssetLogApi.Create, handler = this)

}
