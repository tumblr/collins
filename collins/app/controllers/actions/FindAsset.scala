package controllers
package actions

import forms._
import models.{Asset, AssetFinder, AssetType, MetaWrapper, Page, PageParams, Status}
import util.{AttributeResolver, Helpers}

import play.api.data._
import play.api.libs.json._
import play.api.mvc._

private[controllers] object FindAsset {
  val params = Set("tag", "attribute", "type", "status", "createdAfter", "createdBefore", "updatedAfter", "updatedBefore")
  val ff = Form(
    of(AssetFinderWrapper.apply _, AssetFinderWrapper.unapply _)(
      "" -> of(AssetFinder.apply _, AssetFinder.unapply _)(
        "tag" -> optional(text(1)),
        "status" -> optional(of[Status.Enum]),
        "type" -> optional(of[AssetType.Enum]),
        "createdAfter" -> optional(date(Helpers.ISO_8601_FORMAT)),
        "createdBefore" -> optional(date(Helpers.ISO_8601_FORMAT)),
        "updatedAfter" -> optional(date(Helpers.ISO_8601_FORMAT)),
        "updatedBefore" -> optional(date(Helpers.ISO_8601_FORMAT))
      ),
      "attribute" -> optional(text(3)).verifying("Invalid attribute specified", res => res match {
        case None => true
        case Some(s) => s.split(";", 2).size == 2
      })
    )
  )

  def formatResultAsRd(results: Page[Asset]): ResponseData = {
    ResponseData(Results.Ok, JsObject(results.getPaginationJsMap() ++ Map(
      "Data" -> JsArray(results.items.map { i => JsObject(i.toJsonMap) }.toList)
    )), results.getPaginationHeaders)
  }
}

private[controllers] class FindAsset() {

  def formatFormErrors(errors: Seq[FormError]): String = {
    errors.map { e =>
      e.key match {
        case "tag" | "attribute" | "type" | "status" => "%s - %s".format(e.key, e.message)
        case key if key.startsWith("created") => "%s must be an ISO8601 date".format(key)
        case key if key.startsWith("updated") => "%s must be an ISO8601 date".format(key)
      }
    }.mkString(", ")
  }

  type Validated = (AttributeResolver.ResultTuple,AssetFinder)
  def validateRequest()(implicit req: Request[AnyContent]): Either[String,Validated] = {
    FindAsset.ff.bindFromRequest.fold(
      errorForm => Left(formatFormErrors(errorForm.errors)),
      success => {
        val af = success.af
        val attributeMap = try {
          success.formatAttribute(req.queryString)
        } catch {
          case e => return Left(e.getMessage)
        }
        val resolvedMap = try {
          AttributeResolver(attributeMap)
        } catch {
          case e => return Left(e.getMessage)
        }
        Right((resolvedMap,af))
      }
    )
  }

  def apply(page: Int, size: Int, sort: String)(implicit req: Request[AnyContent]): Either[String,Page[Asset]] = {
    validateRequest() match {
      case Left(err) => Left("Error executing search: " + err)
      case Right(valid) =>
        val pageParams = PageParams(page, size, sort)
        val results = MetaWrapper.findAssets(pageParams, valid._1, valid._2)
        Right(results)
    }
  }
}

private[actions] case class AssetFinderWrapper(af: AssetFinder, attribs: Option[String]) {
  def formatAttribute(req: Map[String,Seq[String]]): Map[String,String] = {
    attribs.map { _ =>
      req("attribute").foldLeft(Map[String,String]()) { case(total,cur) =>
        val split = cur.split(";", 2)
        if (split.size == 2) {
          total ++ Map(split(0) -> split(1))
        } else {
          throw new IllegalArgumentException("attribute %s not formatted as key;value".format(cur))
        }
      }
    }.getOrElse(Map.empty)
  }
}


