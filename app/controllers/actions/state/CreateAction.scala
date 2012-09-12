package controllers
package actions
package state

import forms._
import validators.{ParamValidation, StringUtil}

import models.{State, Status => AStatus}
import util.MessageHelper
import util.security.SecuritySpecification

import play.api.data.Form
import play.api.data.Forms._

object CreateAction {
  object Messages extends MessageHelper("controllers.AssetStateApi.createState") {
    def invalidName = messageWithDefault("invalidName", "The specified name is invalid")
    def invalidDescription = messageWithDefault("invalidDescription",
      "The specified description is invalid")
    def invalidStatus = rootMessage("asset.status.invalid")
    def invalidLabel = messageWithDefault("invalidLabel", "The specified label is invalid")
  }
}

/**
 * Create a new asset state
 *
 * @apigroup AssetState
 * @apimethod PUT
 * @apiurl /api/state/:name
 * @apiparam name String A unique name between 2 and 32 characters, must be upper case
 * @apiparam status Option[String] Status name to bind this state to, or Any to bind to all status
 * @apiparam label String A friendly display label between 2 and 32 characters
 * @apiparam description String A longer description of the state between 2 and 255 characters
 * @apirespond 201 success
 * @apirespond 400 invalid input
 * @apirespond 409 name already in use
 * @apirespond 500 error saving state
 * @apiperm controllers.AssetStateApi.createState
 * @collinsshell {{{
 *  collins-shell state create --name=NAME --label=LABEL --description='DESCRIPTION' [--status=Status]
 * }}}
 * @curlexample {{{
 *  curl -v -u blake:admin:first --basic \
 *    -d label='Test Label' \
 *    -d description='This is for testing' \
 *    http://localhost:9000/api/state/TESTING
 * }}}
 */
case class CreateAction(
  name: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with ParamValidation {

  import CreateAction.Messages._

  case class ActionDataHolder(state: State) extends RequestDataHolder

  val stateForm = Form(tuple(
    "id" -> ignored(0:Int),
    "status" -> validatedOptionalText(1),
    "label" -> validatedText(2, 32),
    "description" -> validatedText(2, 255)
  ))

  override def validate(): Validation = stateForm.bindFromRequest()(request).fold(
    err => Left(RequestDataHolder.error400(fieldError(err))),
    form => {
      val (id, statusOpt, label, description) = form
      val validatedName = StringUtil.trim(name).filter(s => s.length > 1 && s.length <= 32)
      val statusId = getStatusId(statusOpt)
      if (statusOpt.isDefined && !statusId.isDefined) {
        Left(RequestDataHolder.error400(invalidStatus))
      } else if (!validatedName.isDefined || State.findByName(validatedName.get).isDefined) {
        Left(RequestDataHolder.error409(invalidName))
      } else {
        Right(
          ActionDataHolder(State(0, statusId.getOrElse(State.ANY_STATUS), validatedName.get, label, description))
        )
      }
    }
  )

  override def execute(rdh: RequestDataHolder) = rdh match {
    case ActionDataHolder(state) => try {
      State.create(state) match {
        case ok if ok.id > 0 =>
          Api.statusResponse(true, Status.Created)
        case bad =>
          Api.statusResponse(false, Status.InternalServerError)
      }
    } catch {
      case e =>
        Api.errorResponse("Failed to add state", Status.InternalServerError, Some(e))
    }
  }

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("name").isDefined => invalidName
    case e if e.error("label").isDefined => invalidLabel
    case e if e.error("description").isDefined => invalidDescription
    case e if e.error("status").isDefined => invalidStatus
    case n => fuck
  }

  protected def getStatusId(status: Option[String]): Option[Int] = status.flatMap { s =>
    (s.toUpperCase == State.ANY_NAME.toUpperCase) match {
      case true => Some(State.ANY_STATUS)
      case false => AStatus.findByName(s).map(_.id)
    }
  }

}
