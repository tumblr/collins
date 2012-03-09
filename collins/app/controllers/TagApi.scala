package controllers

import models.{AssetMeta, AssetMetaValue}
import util.SecuritySpec

import play.api.libs.json._
import play.api.mvc.Results

trait TagApi {
  this: Api with SecureController =>

  def getTags = SecureAction { implicit req =>
    val js = AssetMeta.findAll().sortBy(_.name).map { am =>
      val fields = Seq(
        ("name" -> JsString(am.name)),
        ("label" -> JsString(am.label)),
        ("description" -> JsString(am.description))
      )
      JsObject(fields)
    }
    val jsArray = JsArray(js.toList)
    val data = ResponseData(Results.Ok, JsObject(Seq("tags" -> jsArray)))
    formatResponseData(data)
  }(SecuritySpec(true))

  def getTagValues(tag: String) = SecureAction { implicit req =>
    val response =
      AssetMeta.findByName(tag).map { m =>
        val s: Set[String] = AssetMetaValue.findMetaValues(m.getId).sorted.toSet
        val js = JsObject(Seq("values" -> JsArray(s.toList.map(JsString(_)))))
        ResponseData(Results.Ok, js)
      }.getOrElse(Api.getErrorMessage("Tag not found", Results.NotFound))
    formatResponseData(response)
  }(SecuritySpec(true))

}
