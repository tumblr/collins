package controllers

import models._
import util.{AssetStateMachine, IpmiCommandProcessor, IpmiIdentifyCommand, SecuritySpec}
import util.Helpers.formatPowerPort
import views._

import akka.util.duration._
import play.api._
import play.api.libs.json._
import play.api.mvc._
import play.api.data._

trait Resources extends Controller {
  this: SecureController =>

  import AssetMeta.Enum.ChassisTag
  implicit val spec = SecuritySpec(isSecure = true, Nil)
  val infraSpec = SecuritySpec(isSecure = true, Seq("infra"))

  def index = SecureAction { implicit req =>
    Ok(html.resources.index(AssetMeta.getViewable()))
  }

  def createForm(assetType: String) = SecureAction { implicit req =>
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

  val assetCreator = new AssetApi.CreateAsset()
  def createAsset(atype: String) = SecureAction { implicit req =>
    Form("tag" -> requiredText).bindFromRequest.fold(
      noTag => Redirect(app.routes.Resources.createForm(atype)).flashing("error" -> "A tag must be specified"),
      withTag => {
        val rd = assetCreator(withTag)
        rd.status match {
          case Results.Created =>
            Redirect(app.routes.Resources.index).flashing("success" -> "Asset successfully created")
          case _ =>
            val errJs = rd.data.value.getOrElse("ERROR_DETAILS", JsString("Error processing request"))
            val errStr = errJs.as[String]
            Redirect(app.routes.Resources.createForm(atype)).flashing("error" -> errStr)
        }
      }
    )
  }(infraSpec)

  /**
   * Find assets by query parameters, special care for ASSET_TAG
   */
  val findAssets = new AssetApi.FindAsset()
  def find(page: Int, size: Int, sort: String) = SecureAction { implicit req =>
    Form("ASSET_TAG" -> requiredText).bindFromRequest.fold(
      noTag => {
        findAssets(page, size, sort)(rewriteRequest(req))
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
    Asset.findById(id).flatMap { asset =>
      intakeAllowed(asset) match {
        case true => Some(asset)
        case false => None
      }
    } match {
      case None =>
        Redirect(app.routes.Resources.index).flashing("error" -> "Can not intake host that isn't New")
      case Some(asset) => stage match {
        case 2 =>
          logger.debug("intake stage 2")
          Ok(html.resources.intake2(asset))
        case 3 =>
          logger.debug("intake stage 3")
          intakeStage3(asset)
        case 4 =>
          logger.debug("intake stage 4")
          intakeStage4(asset)
        case n =>
          logger.debug("intake stage " + n)
          AsyncResult {
            IpmiCommandProcessor.send(IpmiIdentifyCommand(asset, 30.seconds)) { opt =>
              opt match {
                case Some(error) =>
                  Redirect(app.routes.HelpPage.index(Help.IpmiError().id)).flashing(
                    "message" -> error
                  )
                case None =>
                  Ok(html.resources.intake(asset, None))
              }
            }
          }
      }
    }
  }(infraSpec)

  private def intakeStage3Form(asset: Asset): Form[(String,String,String,String)] = {
    import models.AssetMeta.Enum._
    Form(
      of(
        ChassisTag.toString -> requiredText(1),
        RackPosition.toString -> requiredText(1),
        formatPowerPort("A") -> requiredText(1),
        formatPowerPort("B") -> requiredText(1)
      ) verifying ("Port A must not equal Port B", result => result match {
        case(c,r,pA,pB) => pA != pB
      }) verifying ("Chassis must match actual chassis", result => result match {
        case(c,r,pA,pB) => asset.getAttribute(ChassisTag).isDefined
      })
    )
  }

  /**
   * Handle stage 4 validation
   *
   * We should have gotten a: CHASSIS_TAG, RACK_POSITION, POWER_PORT_A, POWER_PORT_B
   * Validate that CHASSIS_TAG matches one on Asset
   * Validate that _A and _B are on different PDU's and are PDU's we know about
   * Validate that RACK_POSITION is a rack we know about
   */
  private def intakeStage4(asset: Asset)(implicit req: Request[AnyContent]) = {
    import AssetMeta.Enum._
    def handleError(e: Throwable) = {
      val chassis_tag: String = asset.getAttribute(ChassisTag).get.getValue
      val formWithErrors = intakeStage3Form(asset).bindFromRequest
      val msg = "Error on host intake: %s".format(e.getMessage)
      val flash = Flash(Map("error" -> msg))
      logger.error(msg)
      InternalServerError(html.resources.intake3(asset, chassis_tag, formWithErrors)(flash, req))
    }
    intakeStage3Form(asset).bindFromRequest.fold(
      formWithErrors => {
        val chassis_tag: String = asset.getAttribute(ChassisTag).get.getValue
        BadRequest(html.resources.intake3(asset, chassis_tag, formWithErrors))
      },
      success => success match {
        case(tag, position, portA, portB) => {
          val assetId = asset.getId
          val values = List(
            AssetMetaValue(assetId, RackPosition.id, position),
            AssetMetaValue(assetId, PowerPort.id, portA),
            AssetMetaValue(assetId, PowerPort.id, portB))
          try {
            Model.withTransaction { implicit conn =>
              val created = AssetMetaValue.create(values)
              require(created == values.length,
                "Should have had %d values, had %d".format(values.length, created))
              AssetStateMachine(asset).update().executeUpdate()
            }
            Redirect(app.routes.Resources.index).flashing(
              "success" -> "Successfull intake of %s".format(asset.tag)
            )
          } catch {
            case e: Throwable => handleError(e)
          }
        }
      }
    )
  }

  /**
   * Handle stage 3 validation
   *
   * Lookup CHASSIS_TAG in HTTP parameters
   * On error, back to intake2
   * On success, retrieve ChassisTag for asset
   * Check equality of specified tag and actual tag
   * Special cases where:
   *   - Specified tag and stored tag don't match
   *     - Display error to user
   *   - No tag is associated with the asset
   *     - Display error to user
   *   - Multiple chassis tags are associated with an asset
   *     - 500, this should not happen
   */
  private def intakeStage3(asset: Asset)(implicit req: Request[AnyContent]) = try {
    Form("CHASSIS_TAG" -> text).bindFromRequest.fold(
      errors => {
        val flash  = Flash(Map("warning" -> "No CHASSIS_TAG submitted"))
        BadRequest(html.resources.intake2(asset)(flash, req))
      },
      chassis_tag => asset.getAttribute(ChassisTag).map { attrib =>
        chassis_tag == attrib.getValue match {
          case true =>
            Ok(html.resources.intake3(asset, chassis_tag, intakeStage3Form(asset)))
          case false =>
            val msg = "Asset %s has chassis tag '%s', not '%s'".format(
              asset.tag, attrib.getValue, chassis_tag)
            val flash = Flash(Map("error" -> msg))
            Ok(html.resources.intake2(asset)(flash, req))
        }
      }.getOrElse {
        val msg = "Asset %s does not have an associated chassis tag".format(
          asset.tag
        )
        val flash = Flash(Map("error" -> msg))
        Ok(html.resources.intake2(asset)(flash, req))
      }
    )
  } catch {
    case e: IndexOutOfBoundsException =>
      val msg = "Asset %s has multiple chassis tags associated with it.".format(
        asset.tag
      )
      val flash = Flash(Map("error" -> msg))
      logger.error(msg)
      InternalServerError(html.resources.intake2(asset)(flash, req))
  }

  /**
   * Given a asset tag, find the associated asset
   */
  private def findByTag(tag: String, page: PageParams)(implicit r: Request[AnyContent]) = {
    Asset.findByTag(tag) match {
      case None => Asset.findLikeTag(tag, page) match {
        case Page(_, _, _, 0) =>
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
    val rightRole = hasRole(getUser(r), Seq("infra"))
    isNew && rightType && rightRole
  }

  /**
   * Rewrite k/v pairs into an attribute=k;v map
   */
  private def rewriteRequest(req: Request[AnyContent]): Request[AnyContent] = {
    val respectedKeys = AssetApi.FindAsset.params
    val nonEmpty = stripQuery(req.queryString)
    val grouped = nonEmpty.groupBy { case(k, v) =>
      respectedKeys.contains(k)
    }
    val respectedParams = grouped.getOrElse(true, Map[String,Seq[String]]())
    val rewrittenParams = grouped.get(false).map { unknownParams =>
      unknownParams.map { case(k,v) =>
        k -> v.map { s => ("%s;%s".format(k,s)) }
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
      def cookies = req.cookies
      def body = req.body
    }
  }

}
