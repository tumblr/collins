package controllers
package actions
package asset

import models.AssetLifecycle
import util.SecuritySpecification
import validators.StringUtil

import play.api.data.Form
import play.api.data.Forms._

case class DeleteAction(
  _assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(options: Map[String,String]) extends RequestDataHolder

  lazy val reason: Option[String] = Form(
    "reason" -> optional(text(1))
  ).bindFromRequest()(request).fold(
    err => None,
    str => str.flatMap(StringUtil.trim(_))
  )

  override def validate(): Either[RequestDataHolder,RequestDataHolder] = {
    withValidAsset(_assetTag) { asset =>
      val options = reason.map(r => Map("reason" -> r)).getOrElse(Map.empty)
      Right(ActionDataHolder(options))
    }
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(options) =>
      AssetLifecycle.decommissionAsset(definedAsset, options) match {
        case Left(throwable) =>
          handleError(
            RequestDataHolder.error409("Illegal state transition: %s".format(throwable.getMessage))
          )
        case Right(status) =>
          Api.statusResponse(status)
      }
  }

}

