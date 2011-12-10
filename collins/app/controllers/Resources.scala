package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.core.QueryStringBindable

import models._
import util.SecuritySpec
import views._

object Resources extends SecureWebController {
  implicit val spec = SecuritySpec(isSecure = true, Nil)

  type Help = Help.Value
  object Help extends Enumeration {
    val IpmiLight = Value(1)
  }

  def help(htype: Help) = SecureAction { implicit req =>
    Ok(html.resources.help(htype))
  }

  def index = SecureAction { implicit req =>
    Ok(html.resources.index(AssetMeta.getViewable()))
  }

  /**
   * Find assets by query parameters, special care for TUMBLR_TAG
   */
  def find = SecureAction { implicit req =>
    Form("TUMBLR_TAG" -> text).bindFromRequest.fold(
      noTag => rewriteQuery(req) match {
        case Nil =>
          Redirect(routes.Resources.index).flashing(
            "message" -> "No query specified"
          )
        case q => findByMeta(q)
      },
      tumblr_tag => {
        logger.debug("Tumblr Tag: " + tumblr_tag)
        findBySecondaryId(tumblr_tag)
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
        Redirect(routes.Resources.index).flashing("error" -> "Can not intake host that isn't New")
      case Some(asset) => stage match {
        case 2 =>
          logger.debug("intake stage 2")
          Ok(html.resources.intake2(asset))
        case 3 =>
          logger.debug("intake stage 3")
          intakeStage3(asset)
        case 4 =>
          logger.debug("intake stage 4")
          Ok("Done")
        case n =>
          logger.debug("intake stage " + n)
          Ok(html.resources.intake(asset))
      }
    }
  }(SecuritySpec(isSecure = true, Seq("infra")))

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
      chassis_tag => asset.getAttribute(AssetMeta.Enum.ChassisTag).map { attrib =>
        chassis_tag == attrib.getValue match {
          case true =>
            Ok(html.resources.intake3(asset, chassis_tag))
          case false =>
            val msg = "Asset %s has chassis tag '%s', not '%s'".format(
              asset.secondaryId, attrib.getValue, chassis_tag)
            val flash = Flash(Map("error" -> msg))
            Ok(html.resources.intake2(asset)(flash))
        }
      }.getOrElse {
        val msg = "Asset %s does not have an associated chassis tag".format(
          asset.secondaryId
        )
        val flash = Flash(Map("error" -> msg))
        Ok(html.resources.intake2(asset)(flash))
      }
    )
  } catch {
    case e: IndexOutOfBoundsException =>
      val msg = "Asset %s has multiple chassis tags associated with it.".format(
        asset.secondaryId
      )
      val flash = Flash(Map("error" -> msg))
      logger.error(msg)
      InternalServerError(html.resources.intake2(asset)(flash))
  }

  /**
   * Given a secondary_id (tumblr tag), find the associated asset
   */
  protected def findBySecondaryId[A](id: String)(implicit r: Request[A]) = {
    Asset.findBySecondaryId(id) match {
      case None =>
        Redirect(routes.Resources.index).flashing("message" -> "Could not find asset with specified tumblr tag")
      case Some(asset) =>
        intakeAllowed(asset) match {
          case true =>
            Redirect(routes.Resources.intake(asset.id, 1))
          case false =>
            Ok(html.resources.list(Seq(asset)))
        }
    }
  }

  protected def intakeAllowed[A](asset: Asset)(implicit r: Request[A]): Boolean = {
    val isNew = asset.isNew
    val rightType = asset.assetType == AssetType.Enum.ServerNode.id
    val rightRole = hasRole(getUser(r), Seq("infra"))
    logger.info("intakeAllowed - New: %s, Right Type: %s, Right Role: %s".format(
      isNew, rightType, rightRole
    ))
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
        Redirect(routes.Resources.index).flashing("message" -> "No results found")
      case r =>
        Ok(html.resources.list(r))
    }
  }

  // Implicit exists to allow for controller binding of routes that leverage Help
  implicit def bindableHelp = new QueryStringBindable[Resources.Help] {
    def bind(key: String, params: Map[String, Seq[String]]) = 
      params.get(key).flatMap(_.headOption).map { i =>
        try {
          Right(Resources.Help(Integer.parseInt(i)))
        } catch {
          case e: Exception => Left("Cannot parse parameter " + key + " as Help")
        }
      }

    def unbind(key: String, value: Resources.Help) = key + "=" + value.id
  }

}
