package collins.controllers.actions.assettype

import scala.concurrent.Future

import play.api.data.Form
import play.api.data.Forms.ignored
import play.api.data.Forms.tuple
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import collins.controllers.Api
import collins.controllers.SecureController
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.controllers.validators.ParamValidation
import collins.models.AssetType
import collins.util.MessageHelper
import collins.util.security.SecuritySpecification
import collins.validation.StringUtil

object CreateAction {
  object Messages extends MessageHelper("controllers.AssetTypeApi.createAssetType") {
    def inUseName = messageWithDefault("inUseName", "The specified name is already in use")
    def invalidName = messageWithDefault("invalidName", "The specified name is invalid")
    def invalidLabel = messageWithDefault("invalidLabel", "The specified label is invalid")
  }
}

/**
 * Create a new asset type
 *
 * @apigroup AssetType
 * @apimethod PUT
 * @apiurl /api/assettype/:name
 * @apiparam name String A unique name between 2 and 32 characters, must be upper case
 * @apiparam label String A friendly display label between 2 and 32 characters
 * @apirespond 201 success
 * @apirespond 400 invalid input
 * @apirespond 409 name already in use
 * @apirespond 500 error saving asset type
 * @apiperm controllers.AssetTypeApi.createAssetType
 * @collinsshell {{{
 *  collins-shell asset_type create --name=NAME --label=LABEL
 * }}}
 * @curlexample {{{
 *  curl -XPUT -u blake:admin:first --basic \
 *    -d label='Service Asset Type' \
 *    http://localhost:9000/api/assettype/SERVICE
 * }}}
 */
case class CreateAction(
  name: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with ParamValidation {

  import CreateAction.Messages._

  case class ActionDataHolder(atype: AssetType) extends RequestDataHolder

  val atypeForm = Form(tuple(
    "id" -> ignored(0:Int),
    "label" -> validatedText(2, 32)
  ))

  override def validate(): Validation = atypeForm.bindFromRequest()(request).fold(
    err => Left(RequestDataHolder.error400(fieldError(err))),
    form => {
      val (id, label) = form
      val validatedName = StringUtil.trim(name)
                            .filter(s => s.length > 1 && s.length <= 32)
                            .map(_.toUpperCase)
      if (AssetType.findByName(validatedName.get).isDefined) {
        Left(RequestDataHolder.error409(inUseName))
      } else if (!validatedName.isDefined) {
        Left(RequestDataHolder.error400(invalidName))
      } else {
        Right(
          ActionDataHolder(AssetType(validatedName.get, label, 0))
        )
      }
    }
  )

  override def execute(rdh: RequestDataHolder) = Future {
    rdh match {
      case ActionDataHolder(assettype) => try {
        AssetType.create(assettype) match {
          case ok if ok.id > 0 =>
            Api.statusResponse(true, Status.Created)
          case bad =>
            Api.statusResponse(false, Status.InternalServerError)
        }
      } catch {
        case e: Throwable =>
          Api.errorResponse("Failed to add asset type", Status.InternalServerError, Some(e))
      }
    }
  }

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("name").isDefined => invalidName
    case e if e.error("label").isDefined => invalidLabel
    case n => fuck
  }

}
