package controllers

import actions.asset.{CreateAction, FindSimilarAction,SolrFindAction}
import actions.resources.{FindAction, IntakeStage1Action, IntakeStage2Action, IntakeStage3Action}

import models._
import views._

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import collins.solr.Solr

trait Resources extends Controller {
  this: SecureController =>

  def index = SecureAction { implicit req =>
    Ok(html.resources.index(AssetMeta.getViewable())).withHeaders("Content-Language" -> "en")
  }(Permissions.Resources.Index)


  def searchSolr(query: String, details: String, page: Int, size: Int, sort: String, sortField: String) = 
    SolrFindAction(PageParams(page, size, sort, sortField), query, (new Truthy(details)).isTruthy, "tag", Permissions.Resources.Find, this)
  

  def displayCreateForm(assetType: String) = SecureAction { implicit req =>
    val atype: Option[AssetType.Enum] = try {
      Some(AssetType.Enum.withName(assetType))
    } catch {
      case _ => None
    }
    atype match {
      case Some(t) => t match {
        case AssetType.Enum.ServerNode =>
          Redirect(app.routes.Resources.index).flashing("error" -> "Server Node not supported for creation")
        case _ =>
          Ok(html.resources.create(t))
      }
      case None =>
        Redirect(app.routes.Resources.index).flashing("error" -> "Invalid asset type specified")
    }
  }(Permissions.Resources.CreateForm)

  def createAsset(atype: String) = CreateAction(
    None, Some(atype), Permissions.Resources.CreateAsset, this
  )

  /**
   * Find assets by query parameters, special care for ASSET_TAG
   */
  def find(page: Int, size: Int, sort: String, operation: String, sortField: String) = FindAction(
    PageParams(page, size, sort, sortField), operation, sortField, Permissions.Resources.Find, this
  )

  def similar(tag: String, page: Int, size: Int, sort: String) = 
    FindSimilarAction(tag, PageParams(page, size, sort, "sparse"), Permissions.Resources.Find, this)

  def intake(id: Long, stage: Int = 1) = stage match {
    case 2 => IntakeStage2Action(id, Permissions.Resources.Intake, this)
    case 3 => IntakeStage3Action(id, Permissions.Resources.Intake, this)
    case n => IntakeStage1Action(id, Permissions.Resources.Intake, this)
  }

}
