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
    withAssetFromTag(tag) { asset =>
      val exposeCredentials = hasRole(user.get, Seq("infra"))
      val allAttributes = asset.getAllAttributes.exposeCredentials(exposeCredentials)
      ResponseData(Results.Ok, allAttributes.toJsonObject, attachment = Some(allAttributes))
    }.map { data =>
      OutputType.isHtml(req) match {
        case true => data.status match {
          case Results.Ok =>
            val attribs = data.attachment.get.asInstanceOf[Asset.AllAttributes]
            Results.Ok(html.asset.show(attribs))
          case _ =>
            Redirect(app.routes.Resources.index).flashing(
              "message" -> ("Could not find asset with tag " + tag)
            )
        }
        case _ => formatResponseData(data)
      }
    }
  }}(SecuritySpec(true, Nil))

  // GET /api/assets?params
  private val finder = new actions.FindAsset()
  def getAssets(page: Int, size: Int, sort: String) = SecureAction { implicit req =>
    val rd = finder(page, size, sort) match {
      case Left(err) => getErrorMessage(err)
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
  val updateAsset = new UpdateAsset()

  // DELETE /api/asset/:tag
  def deleteAsset(tag: String) = SecureAction { implicit req =>
    import com.twitter.util.StateMachine.InvalidStateTransition
    val responseData = withAssetFromTag(tag) { asset =>
      Model.withTransaction { implicit con =>
        try {
          AssetStateMachine(asset).decommission().executeUpdate()
          AssetLog.create(AssetLog.informational(
            asset,
            "Asset decommissioned successfully",
            AssetLog.Formats.PlainText,
            AssetLog.Sources.Internal
          ))
          ResponseData(Results.Ok, JsObject(Map("SUCCESS" -> JsBoolean(true))))
        } catch {
          case e: InvalidStateTransition =>
            val msg = "Only assets in a cancelled state can be decommissioned"
            getErrorMessage(msg, Results.Status(StatusValues.CONFLICT))
          case e =>
            val msg = "Error saving response: %s".format(e.getMessage)
            getErrorMessage(msg, Results.InternalServerError)
        }
      }
    }
    formatResponseData(responseData)
  }(SecuritySpec(true, Seq("infra")))

  private[AssetApi] class UpdateAsset(perms: Seq[String] = Seq("infra")) {
    import play.api.libs.Files
    val updateForm = Form(of(
      "lshw" -> optional(text(1)),
      "lldp" -> optional(text(1)),
      "chassis_tag" -> optional(text(1)),
      "attribute" -> optional(text(3)).verifying("Invalid attribute specified", res => res match {
        case None => true
        case Some(v) => v.split(";", 2).size == 2
      })
    ))

    protected def getFormFile(key: String)(implicit req: Request[AnyContent]): Map[String,String] = {
      req.body match {
        case AnyContentAsMultipartFormData(mdf) => mdf.file(key) match {
          case Some(temporaryFile) =>
            val src = io.Source.fromFile(temporaryFile.ref.file)
            val txt = src.mkString
            src.close()
            Map(key -> txt)
          case None => Map.empty
        }
        case _ => Map.empty
      }
    }

    def validateRequest(asset: Asset)(implicit req: Request[AnyContent]): Either[String,Map[String,String]] = {
      updateForm.bindFromRequest.fold(
        hasErrors => Left("Error processing form data"),
        success => {
          val (lshw, lldp, chassis_tag, attribute) = success
          val map: Map[String,String] = Map.empty ++
            lshw.map { s => Map("lshw" -> s) }.getOrElse(getFormFile("lshw")) ++
            lldp.map { s => Map("lldp" -> s) }.getOrElse(getFormFile("lldp")) ++
            chassis_tag.map { s => Map("chassis_tag" -> s) }.getOrElse(Map.empty) ++
            attribute.map { attrib =>
              val attribs = attrib.split(";", 2)
              Map(attribs(0) -> attribs(1))
            }.getOrElse(Map.empty)
          Right(map)
        }
      )
    }

    def apply(tag: String) = SecureAction { implicit req =>
      val responseData = withAssetFromTag(tag) { asset =>
        validateRequest(asset) match {
          case Left(error) => getErrorMessage(error)
          case Right(options) =>
            AssetLifecycle.updateAsset(asset, options) match {
              case Left(error) => getErrorMessage(error.getMessage)
              case Right(success) =>
                ResponseData(Results.Ok, JsObject(Map("SUCCESS" -> JsBoolean(success))))
            }
        }
      }
      formatResponseData(responseData)
    }(SecuritySpec(true, Seq("infra")))
  }

}
