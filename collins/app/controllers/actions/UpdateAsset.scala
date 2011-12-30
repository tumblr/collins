package controllers
package actions

import models.{Asset, AssetLifecycle}

import play.api.data._
import play.api.mvc._

private[controllers] class UpdateAsset() { 
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

  def apply(tag: String)(implicit req: Request[AnyContent]): Either[ResponseData,Boolean] = {
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

