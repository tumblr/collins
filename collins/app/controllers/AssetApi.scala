package controllers

import models.{Status => AStatus}
import models._
import util._
import views.html

import play.api.data._
import play.api.http.{Status => StatusValues}
import play.api.mvc._
import play.api.libs.json._

import java.util.Date

trait AssetApi {
  this: Api with SecureController =>

  private lazy val lshwConfig = Helpers.subAsMap("lshw")

  def getAsset(tag: String) = Authenticated { user => Action { implicit req =>
    val result = Api.withAssetFromTag(tag) { asset =>
      val exposeCredentials = hasRole(user.get, Seq("infra"))
      val allAttributes = asset.getAllAttributes.exposeCredentials(exposeCredentials)
      Right(ResponseData(Results.Ok, allAttributes.toJsonObject, attachment = Some(allAttributes)))
    }
    result match {
      case Left(err) =>
        if (OutputType.isHtml(req)) {
          Redirect(app.routes.Resources.index).flashing(
            "message" -> ("Could not find asset with tag " + tag)
          )
        } else {
          formatResponseData(err)
        }
      case Right(success) =>
        if (OutputType.isHtml(req)) {
          val attribs = success.attachment.get.asInstanceOf[Asset.AllAttributes]
          Results.Ok(html.asset.show(attribs))
        } else {
          formatResponseData(success)
        }
    }
  }}(SecuritySpec(true, Nil))

  // GET /api/assets?params
  private val finder = new actions.FindAsset()
  def getAssets(page: Int, size: Int, sort: String) = SecureAction { implicit req =>
    val rd = finder(page, size, sort) match {
      case Left(err) => Api.getErrorMessage(err)
      case Right(success) => actions.FindAsset.formatResultAsRd(success)
    }
    formatResponseData(rd)
  }(SecuritySpec(true, Nil))

  // PUT /api/asset/:tag
  private val assetCreator = new actions.CreateAsset()
  def createAsset(tag: String) = SecureAction { implicit req =>
    formatResponseData(assetCreator(tag))
  }(SecuritySpec(true, Seq("infra")))

  // POST /api/asset/:tag
  private val assetUpdater = new actions.UpdateAsset()
  def updateAsset(tag: String) = SecureAction { implicit req =>
    val rd = assetUpdater(tag) match {
      case Left(err) => err
      case Right(status) =>
        ResponseData(Results.Ok, JsObject(Map("SUCCESS" -> JsBoolean(status))))
    }
    formatResponseData(rd)
  }(SecuritySpec(true, Seq("infra")))

  // DELETE /api/asset/:tag
  def deleteAsset(tag: String) = SecureAction { implicit req =>
    import com.twitter.util.StateMachine.InvalidStateTransition
    val result = Api.withAssetFromTag(tag) { asset =>
      try {
        Model.withTransaction { implicit con =>
          AssetStateMachine(asset).decommission().executeUpdate()
          AssetLog.informational(
            asset,
            "Asset decommissioned successfully",
            AssetLog.Formats.PlainText,
            AssetLog.Sources.Internal
          ).create()
          Right(ResponseData(Results.Ok, JsObject(Map("SUCCESS" -> JsBoolean(true)))))
        }
      } catch {
        case e: InvalidStateTransition =>
          val msg = "Only assets in a cancelled state can be decommissioned"
          Left(Api.getErrorMessage(msg, Results.Status(StatusValues.CONFLICT)))
        case e =>
          val msg = "Error saving response: %s".format(e.getMessage)
          Left(Api.getErrorMessage(msg, Results.InternalServerError))
      } 
    }
    val responseData = result.fold(l => l, r => r)
    formatResponseData(responseData)
  }(SecuritySpec(true, Seq("infra")))


}
