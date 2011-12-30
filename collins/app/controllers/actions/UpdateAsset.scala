package controllers
package actions

import models.{Asset, AssetLifecycle}
import models.AssetMeta.Enum.ChassisTag

import play.api.data._
import play.api.mvc._

private[controllers] case class UpdateAsset(
  lshw: Option[String],
  lldp: Option[String],
  chassisTag: Option[String],
  attribute: Option[String],
  form: Option[Form[UpdateAsset]] = None)
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

  protected def validateRequest(asset: Asset)(implicit req: Request[AnyContent]): Either[String,Map[String,String]] = {
    UpdateAsset.FORM.bindFromRequest.fold(
      hasErrors => Left("Error processing form data"),
      form => {
        val map: Map[String,String] = UpdateAsset.toMap(form) ++
          req.queryString.get("ATTRIBUTE").map(seq => attributes(seq)).getOrElse(Map.empty)
        Right(map)
      }
    )
  }

  def execute(tag: String)(implicit req: Request[AnyContent]): Either[ResponseData,Boolean] = {
    Api.withAssetFromTag(tag) { asset =>
      validateRequest(asset) match {
        case Left(error) => Left(Api.getErrorMessage(error))
        case Right(options) =>
          AssetLifecycle.updateAsset(asset, options) match {
            case Left(error) => Left(Api.getErrorMessage(error.getMessage))
            case Right(success) => Right(success)
          }
      }
    }
  }
}

object UpdateAsset {
  val FORM = Form(
    of(UpdateAsset.apply _, UpdateAsset.unapply _)(
      "LSHW" -> optional(text(1)),
      "LLDP" -> optional(text(1)),
      ChassisTag.toString -> optional(text(1)),
      "ATTRIBUTE" -> optional(text(3)),
      "form" -> ignored(None)
    )
  )
  def get() = new UpdateAsset(None, None, None, None, None)
  def get(form: Form[UpdateAsset]) = new UpdateAsset(None, None, None, None, Some(form))
  def toMap(form: UpdateAsset): Map[String,String] = Map.empty ++
    form.lshw.map(s => Map("LSHW" -> s)).getOrElse(Map.empty) ++
    form.lldp.map(s => Map("LLDP" -> s)).getOrElse(Map.empty) ++
    form.chassisTag.map(s => Map(ChassisTag.toString -> s)).getOrElse(Map.empty) ++
    form.attributes
}
