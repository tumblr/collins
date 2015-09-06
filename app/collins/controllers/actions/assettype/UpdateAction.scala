package collins.controllers.actions.assettype

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.tuple
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.validators.ParamValidation
import collins.models.AssetType
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

/**
 * Update a asset type
 *
 * @apigroup AssetType
 * @apimethod POST
 * @apiurl /api/assettype/:name
 * @apiparam :name String Old name, reference
 * @apiparam name Option[String] new name, between 2 and 32 characters
 * @apiparam label Option[String] A friendly display label between 2 and 32 characters
 * @apirespond 200 success
 * @apirespond 400 invalid input
 * @apirespond 404 invalid name
 * @apirespond 409 name already in use or trying to modify system name
 * @apirespond 500 error saving asset type
 * @apiperm controllers.AssetTypeApi.updateAssetType
 * @collinsshell {{{
 *  collins-shell asset_type update OLDNAME [--name=NAME --label=LABEL]
 * }}}
 * @curlexample {{{
 *  curl -XPOST -u blake:admin:first --basic \
 *    -d name='NEW_NAME' \
 *    http://localhost:9000/api/assettype/OLD_NAME
 * }}}
 */
case class UpdateAction(
  name: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with ParamValidation {

  import CreateAction.Messages._
  import DeleteAction.Messages.systemName

  case class ActionDataHolder(atype: AssetType) extends RequestDataHolder

  val atypeForm = Form(tuple(
    "name" -> validatedOptionalText(2, 32),
    "label" -> validatedOptionalText(2, 32)
  ))

  override def validate(): Validation = atypeForm.bindFromRequest()(request).fold(
    err => Left(RequestDataHolder.error400(fieldError(err))),
    form => {
      val (nameOpt, labelOpt) = form
      StringUtil.trim(name).filter(s => s.length > 1 && s.length <= 32).flatMap { s =>
        AssetType.findByName(s)
      }.map { atype =>
        if (AssetType.isSystemType(atype)) {
          Left(RequestDataHolder.error409(systemName))
        } else {
          validateName(nameOpt)
            .right.map { validatedNameOpt =>
              val named = atypeWithName(atype, validatedNameOpt)
              val labeled = atypeWithLabel(named, labelOpt)
              ActionDataHolder(labeled)
            }
        }
      }.getOrElse {
        Left(RequestDataHolder.error404(invalidName))
      }
    }
  )

  override def execute(rdh: RequestDataHolder) = Future {
    rdh match {
      case ActionDataHolder(atype) => AssetType.update(atype) match {
        case ok if ok >= 0 => Api.statusResponse(true, Status.Ok)
        case notok => Api.statusResponse(false, Status.InternalServerError)
      }
    }
  }

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("name").isDefined => invalidName
    case e if e.error("label").isDefined => invalidLabel
    case n => fuck
  }

  protected def atypeWithName(atype: AssetType, name: Option[String]): AssetType =
    name.map(s => atype.copy(name = s)).getOrElse(atype)
  protected def atypeWithLabel(atype: AssetType, label: Option[String]): AssetType =
    label.map(l => atype.copy(label = l)).getOrElse(atype)

  protected def validateName(nameOpt: Option[String]): Either[RequestDataHolder,Option[String]] = {
    val validatedName: Either[String,Option[String]] = nameOpt match {
      case None =>
        Right(None)
      case Some(n) =>
        StringUtil.trim(n).filter(s => s.length > 1 && s.length <= 32) match {
          case None => Left(invalidName)
          case Some(s) => Right(Some(s))
        }
    }
    validatedName match {
      case Left(err) =>
        Left(RequestDataHolder.error400(err))
      case Right(None) => Right(None)
      case Right(Some(s)) => AssetType.findByName(s) match {
        case None => Right(Some(s))
        case Some(_) => Left(RequestDataHolder.error409(invalidName))
      }
    }
  }

}
