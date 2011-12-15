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

  /**
   * Find assets by query parameters, special care for ASSET_TAG
   */
  def find = SecureAction { implicit req =>
    Form("ASSET_TAG" -> requiredText).bindFromRequest.fold(
      noTag => rewriteQuery(req) match {
        case Nil =>
          Redirect(app.routes.Resources.index).flashing(
            "message" -> "No query specified"
          )
        case q => findByMeta(q)
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
  protected def intakeStage4(asset: Asset)(implicit req: Request[AnyContent]) = {
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
  protected def intakeStage3(asset: Asset)(implicit req: Request[AnyContent]) = try {
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
  protected def findByTag[A](tag: String)(implicit r: Request[A]) = {
    Asset.findByTag(tag) match {
      case None =>
        Redirect(app.routes.Resources.index).flashing("message" -> "Could not find asset with specified asset tag")
      case Some(asset) =>
        intakeAllowed(asset) match {
          case true =>
            Redirect(app.routes.Resources.intake(asset.getId, 1))
          case false =>
            Ok(html.resources.list(Seq(asset)))
        }
    }
  }

  protected def intakeAllowed[A](asset: Asset)(implicit r: Request[A]): Boolean = {
    val isNew = asset.isNew
    val rightType = asset.asset_type == AssetType.Enum.ServerNode.id
    val rightRole = hasRole(getUser(r), Seq("infra"))
    isNew && rightType && rightRole
  }

  // Rewrite a query such that it can be used by findByMeta
  private def rewriteQuery(req: Request[AnyContent]): List[(AssetMeta.Enum, String)] = {
    val requestMap = req.queryString.filter { case(k,vs) => vs.nonEmpty && vs.head.nonEmpty }
    requestMap.map { case(k,vs) =>
      (AssetMeta.Enum.withName(k), vs.head + "%")
    }.toList
  }

  /**
   * Find assets by specified Meta parameters
   */
  protected def findByMeta[A](query: List[(AssetMeta.Enum, String)])(implicit r: Request[A]) = {
    Asset.findByMeta(query) match {
      case Nil =>
        Redirect(app.routes.Resources.index).flashing("message" -> "No results found")
      case r =>
        Ok(html.resources.list(r))
    }
  }

}
