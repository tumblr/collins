package collins.controllers

import collins.controllers.actions.logs.CreateAction
import collins.controllers.actions.logs.FindAction
import collins.controllers.actions.logs.GetAction
import collins.controllers.actions.logs.SolrFindAction
import collins.models.shared.PageParams

trait AssetLogApi {
  this: Api with SecureController =>

  // GET /assets/:tag/logs
  def getAssetLogData(tag: String, page: Int, size: Int, sort: String, filter: String) =
    FindAction(Some(tag), PageParams(page, size, sort, "date"), filter, Permissions.AssetLogApi.Get, this)

  // GET /assets/logs
  def getAllLogData(page: Int, size: Int, sort: String, filter: String) =
    FindAction(None, PageParams(page, size, sort, "date"), filter, Permissions.AssetLogApi.GetAll, this)

  // GET /api/assets/logs/search
  def searchLogs(page: Int, size: Int, sortField: String, sort: String, query: String) = 
    SolrFindAction(query, PageParams(page, size, sort, sortField), Permissions.AssetLogApi.GetAll, this)

  // GET /api/logs/:id
  def getLogData(id: Int) =
    GetAction(id, spec = Permissions.AssetLogApi.Get, handler = this)

  // PUT /api/asset/:tag/log
  def submitLogData(tag: String) =
    CreateAction(tag, spec = Permissions.AssetLogApi.Create, handler = this)

}
