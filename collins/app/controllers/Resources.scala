package controllers

import models._
import util.{Feature, IpmiCommandProcessor, IpmiIdentifyCommand, SecuritySpec}
import views._

import akka.util.duration._
import play.api._
import play.api.mvc._
import play.api.data._

trait Resources extends Controller {
  this: SecureController =>

  import AssetMeta.Enum.ChassisTag
  implicit val spec = SecuritySpec(true)
  val infraSpec = SecuritySpec(true, Seq("infra"))

  def index = SecureAction { implicit req =>
    Ok(html.resources.index(AssetMeta.getViewable()))
  }

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
  }(infraSpec)

  def createAsset(atype: String) = SecureAction { implicit req =>
    Form("tag" -> requiredText).bindFromRequest.fold(
      noTag => Redirect(app.routes.Resources.displayCreateForm(atype)).flashing("error" -> "A tag must be specified"),
      withTag => {
        val rd = new actions.CreateAsset()(withTag)
        rd.status match {
          case Results.Created =>
            Redirect(app.routes.Resources.index).flashing("success" -> "Asset successfully created")
          case _ =>
            val errStr = ApiResponse.getJsonErrorMessage(rd.data, "Error processing request")
            Redirect(app.routes.Resources.displayCreateForm(atype)).flashing("error" -> errStr)
        }
      }
    )
  }(infraSpec)

  /**
   * Find assets by query parameters, special care for ASSET_TAG
   */
  def find(page: Int, size: Int, sort: String, operation: String) = SecureAction { implicit req =>
    Form("ASSET_TAG" -> requiredText).bindFromRequest.fold(
      noTag => {
        val results = new actions.FindAsset()(page, size, sort)(rewriteRequest(req))
        results match {
          case Left(err) =>
            Redirect(app.routes.Resources.index).flashing("error" -> err)
          case Right(success) => success.size match {
            case 0 =>
              Redirect(app.routes.Resources.index).flashing(
                "message" -> "Could not find any matching assets"
              )
            case 1 =>
              Redirect(app.routes.CookieApi.getAsset(success.items(0).tag))
            case n =>
              Results.Ok(html.asset.list(success))
          }
        }
      },
      asset_tag => {
        logger.debug("Got asset tag: " + asset_tag)
        val newReq = newRequestWithQuery(req, stripQuery(req.queryString))
        findByTag(asset_tag, PageParams(page, size, sort))(newReq)
      }
    )
  }

  /**
   * Manage 4 stage asset intake process
   */
  def intake(id: Long, stage: Int = 1) = SecureAction { implicit req =>
    import actions.{Stage1Form, Stage2Form, Stage3Form}
    val asset = Asset.findById(id).flatMap { asset =>
      intakeAllowed(asset) match {
        case true => Some(asset)
        case false => None
      }
    }
    asset match {
      case None =>
        Redirect(app.routes.Resources.index).flashing("error" -> "Can not intake host that isn't New")
      case Some(asset) =>
        val intake = new actions.AssetIntake(stage)
        intake.execute(asset) match {
          case Stage1Form() => AsyncResult {
            IpmiCommandProcessor.send(IpmiIdentifyCommand(asset, 30.seconds)) { opt =>
              opt match {
                case Some(results) =>
                  results.isSuccess match {
                    case true =>
                      Ok(html.resources.intake(asset, None))
                    case false =>
                      Redirect(app.routes.HelpPage.index(Help.IpmiError().id)).flashing(
                        "message" -> results.toString
                      )
                  }
                case None =>
                  Ok(html.resources.intake(asset, None))
              }
            }
          }
          case Stage2Form(chassisTag, Some(form)) =>
            BadRequest(html.resources.intake2(asset, form))
          case Stage2Form(chassisTag, None) =>
            val form = actions.Stage3Form.TRANSITION_FORM(chassisTag)
            Ok(html.resources.intake3(asset, form))
          case form: Stage3Form =>
            form.errorForm.map { formWithErrors =>
              BadRequest(html.resources.intake3(asset, formWithErrors))
            }.getOrElse{
              Redirect(app.routes.Resources.index).flashing(
                "success" -> "Successfull intake of %s".format(asset.tag)
              )
            }
        }
    }
  }(infraSpec)

  /**
   * Given a asset tag, find the associated asset
   */
  private def findByTag(tag: String, page: PageParams)(implicit r: Request[AnyContent]) = {
    Asset.findByTag(tag) match {
      case None => Asset.findLikeTag(tag, page) match {
        case page if page.size == 0 =>
          Redirect(app.routes.Resources.index).flashing("message" -> "Could not find asset with specified asset tag")
        case page =>
          Ok(html.asset.list(page))
      }
      case Some(asset) =>
        intakeAllowed(asset) match {
          case true =>
            Redirect(app.routes.Resources.intake(asset.getId, 1))
          case false =>
            Redirect(app.routes.CookieApi.getAsset(asset.tag))
        }
    }
  }

  private def intakeAllowed(asset: Asset)(implicit r: Request[AnyContent]): Boolean = {
    val isNew = asset.isNew
    val rightType = asset.asset_type == AssetType.Enum.ServerNode.id
    val intakeSupported = Feature("intakeSupported").toBoolean(true)
    val rightRole = getUser(r).isAdmin
    intakeSupported && isNew && rightType && rightRole
  }

  /**
   * Rewrite k/v pairs into an attribute=k;v map
   */
  private def rewriteRequest(req: Request[AnyContent]): Request[AnyContent] = {
    val respectedKeys = actions.FindAsset.params
    val nonEmpty = stripQuery(req.queryString)
    val grouped = nonEmpty.groupBy { case(k, v) =>
      respectedKeys.contains(k)
    }
    val respectedParams = grouped.getOrElse(true, Map[String,Seq[String]]())
    val rewrittenParams = grouped.get(false).map { unknownParams =>
      unknownParams.map { case(k,v) =>
        k -> v.map { s => ("%s;%s".format(k,rewriteAttributeValue(s))) }
      }
    }.getOrElse(Map[String,Seq[String]]())
    val mergedParams: Seq[String] = Seq(
      respectedParams.getOrElse("attribute", Seq[String]()),
      rewrittenParams.values.flatten
    ).flatten
    val finalMap: Map[String,Seq[String]] = mergedParams match {
      case Nil => respectedParams
      case list => respectedParams ++ Map("attribute" -> list)
    }
    newRequestWithQuery(req, finalMap)
  }

  private def rewriteAttributeValue(v: String): String = {
    v match {
      case none if v.toLowerCase == "(none)" =>
        ""
      case _ =>
        v
    }
  }

  private def stripQuery(inputMap: Map[String, Seq[String]]) = {
    val exclude = Set("page", "sort", "size")
    inputMap.filter { case(k,v) =>
      v.forall { _.nonEmpty }
    }.filter { case(k,v) => !exclude.contains(k) }
  }

  private def newRequestWithQuery(req: Request[AnyContent], finalMap: Map[String, Seq[String]]) = {
    new Request[AnyContent] {
      def uri = req.uri
      def path = req.path
      def method = req.method
      def queryString = finalMap
      def headers = req.headers
      def body = req.body
    }
  }

}
