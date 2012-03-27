package controllers
package actions

import forms._

import models.{Asset, AssetLifecycle, Status => AStatus}
import models.AssetMeta.Enum.{ChassisTag, RackPosition}
import util.Helpers.formatPowerPort

import play.api.data._
import play.api.mvc._

object UpdateAsset {
  val FORM = Form(
    of(UpdateAsset.apply _, UpdateAsset.unapply _)(
      "lshw" -> optional(text(1)),
      "lldp" -> optional(text(1)),
      ChassisTag.toString -> optional(text(1)),
      "attribute" -> optional(text(3)),
      RackPosition.toString -> optional(text(1)),
      formatPowerPort("A") -> optional(text(1)),
      formatPowerPort("B") -> optional(text(1)),
      "status" -> optional(of[AStatus.Enum])
    )
  )
  def get() = new UpdateAsset(None, None, None, None, None, None, None, None)
  def get(form: Form[UpdateAsset]) = new UpdateAsset(None, None, None, None, None, None, None, None)
  def toMap(form: UpdateAsset): Map[String,String] = Map.empty ++
    form.lshw.map(s => Map("lshw" -> s)).getOrElse(Map.empty) ++
    form.lldp.map(s => Map("lldp" -> s)).getOrElse(Map.empty) ++
    form.chassisTag.map(s => Map(ChassisTag.toString -> s)).getOrElse(Map.empty) ++
    form.rackPosition.map(s => Map(RackPosition.toString -> s)).getOrElse(Map.empty) ++
    form.powerPort1.map(s => Map(formatPowerPort("A") -> s)).getOrElse(Map.empty) ++
    form.powerPort2.map(s => Map(formatPowerPort("B") -> s)).getOrElse(Map.empty) ++
    form.attributes ++
    form.status.map(s => Map("status" -> s.toString)).getOrElse(Map.empty)
}

private[controllers] case class UpdateAsset(
  lshw: Option[String],
  lldp: Option[String],
  chassisTag: Option[String],
  attribute: Option[String],
  rackPosition: Option[String],
  powerPort1: Option[String],
  powerPort2: Option[String],
  status: Option[AStatus.Enum])
{

  def attributes(): Map[String,String] = attribute.map(s => attributes(s)).getOrElse(Map.empty)
  def attributes(s: String): Map[String,String] = s.split(";", 2) match {
    case Array(k, v) => Map(k -> v)
    case _ =>
      throw new IllegalArgumentException("only attributes of the form key;value are recognized")
  }
  def attributes(seq: Seq[String]): Map[String,String] = seq.foldLeft(Map[String,String]()) { case(map,item) =>
    map ++ attributes(item)
  }

  def execute(tag: String)(implicit req: Request[AnyContent]): Either[ResponseData,Boolean] = {
    Api.withAssetFromTag(tag) { asset =>
      val old = asset.copy()
      validateRequest(asset) match {
        case Left(error) => Left(Api.getErrorMessage(error))
        case Right(options) =>
          val res = if (onlyHasAttributes(req, options)) {
            AssetLifecycle.updateAssetAttributes(asset, options)
          } else if (onlyHasStatus(req, options)) {
            AssetLifecycle.updateAssetStatus(asset, options)
          } else {
            AssetLifecycle.updateAsset(asset, options)
          }
          res match {
            case Left(error) => Left(Api.getErrorMessage(error.getMessage))
            case Right(status) => Right(status)
          }
      }
    }
  }

  protected def onlyHasStatus(req: Request[AnyContent], options: Map[String,String]): Boolean = {
    options.contains("status") match {
      case true =>
        options.size == 1 || (options.size == 2 && options.contains("reason"))
      case false =>
        false
    }
  }

  protected def onlyHasAttributes(req: Request[AnyContent], options: Map[String,String]): Boolean = {
    val a1: Boolean = req.queryString.size == 1 && req.queryString.contains("attribute")
    val a2: Option[Boolean] = req.body.asUrlFormEncoded.map { m =>
      m.size == 1 && m.contains("attribute")
    }
    a2 match {
      case Some(b) => b
      case None => a1
    }
  }

  protected def validateRequest(asset: Asset)(implicit req: Request[AnyContent]): Either[String,Map[String,String]] = {
    UpdateAsset.FORM.bindFromRequest.fold(
      hasErrors => Left("Error processing form data"),
      form => {
        val om1: Option[Map[String,String]] = req.queryString.get("attribute").map(seq => attributes(seq))
        val om2: Option[Map[String,String]] = req.body.asUrlFormEncoded.flatMap { m =>
          m.get("attribute").map { seq =>
            attributes(seq)
          }
        }
        val map: Map[String,String] = UpdateAsset.toMap(form) ++
          om1.orElse(om2).getOrElse(Map.empty)
        Right(map)
      }
    )
  }
}
