package controllers

import play.api._
import play.api.mvc._
import play.api.data._

import models._
import util.{AssetStateMachine, SecuritySpec}
import util.Helpers.formatPowerPort
import views._

trait Resources extends Controller {
  this: SecureController =>

  import AssetMeta.Enum.ChassisTag
  implicit val spec = SecuritySpec(isSecure = true, Nil)

  def index = SecureAction { implicit req =>
    Ok(html.resources.index(AssetMeta.getViewable()))
  }

  def rewriteRequest(req: Request[AnyContent]): Request[AnyContent] = {
    val respectedKeys = AssetApi.FindAsset.params
    val pagination = Set("page", "sort", "size")
    val nonEmpty = req.queryString.filter { case(k,v) =>
      v.forall { _.nonEmpty }
    }.filter { case(k,v) => !pagination.contains(k) }
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
    val finalMap: Map[String,Seq[String]] = respectedParams ++ Map(
      "attribute" -> mergedParams
    )
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
        findByTag(asset_tag)
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
          Ok(html.resources.intake(asset))
      }
    }
  }(SecuritySpec(isSecure = true, Seq("infra")))

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
      InternalServerError(html.resources.intake3(asset, chassis_tag, formWithErrors)(flash))
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
        BadRequest(html.resources.intake2(asset)(flash))
      },
      chassis_tag => asset.getAttribute(ChassisTag).map { attrib =>
        chassis_tag == attrib.getValue match {
          case true =>
            Ok(html.resources.intake3(asset, chassis_tag, intakeStage3Form(asset)))
          case false =>
            val msg = "Asset %s has chassis tag '%s', not '%s'".format(
              asset.tag, attrib.getValue, chassis_tag)
            val flash = Flash(Map("error" -> msg))
            Ok(html.resources.intake2(asset)(flash))
        }
      }.getOrElse {
        val msg = "Asset %s does not have an associated chassis tag".format(
          asset.tag
        )
        val flash = Flash(Map("error" -> msg))
        Ok(html.resources.intake2(asset)(flash))
      }
    )
  } catch {
    case e: IndexOutOfBoundsException =>
      val msg = "Asset %s has multiple chassis tags associated with it.".format(
        asset.tag
      )
      val flash = Flash(Map("error" -> msg))
      logger.error(msg)
      InternalServerError(html.resources.intake2(asset)(flash))
  }

  /**
   * Given a asset tag, find the associated asset
   */
  private def findByTag[A](tag: String)(implicit r: Request[A]) = {
    Asset.findByTag(tag) match {
      case None => Asset.findLikeTag(tag) match {
        case Nil => 
          Redirect(app.routes.Resources.index).flashing("message" -> "Could not find asset with specified asset tag")
        case list =>
          Ok(html.resources.list(list))
      }
      case Some(asset) =>
        intakeAllowed(asset) match {
          case true =>
            Redirect(app.routes.Resources.intake(asset.getId, 1))
          case false =>
            Ok(html.resources.list(Seq(asset)))
        }
    }
  }

  private def intakeAllowed[A](asset: Asset)(implicit r: Request[A]): Boolean = {
    val isNew = asset.isNew
    val rightType = asset.asset_type == AssetType.Enum.ServerNode.id
    val rightRole = hasRole(getUser(r), Seq("infra"))
    isNew && rightType && rightRole
  }

}
