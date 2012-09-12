package controllers
package actions
package state

import forms._
import validators.ParamValidation

import models.{State, Status => AStatus}
import util.MessageHelper
import util.security.SecuritySpecification

import play.api.data.Form
import play.api.data.Forms._

object CreateAction {
  object Messages extends MessageHelper("controllers.assetStateApi.createState") {
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
 * @apiperm controllers.assetStateApi.createState
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

  val stateForm = Form(tuple(
    "id" -> ignored(0:Int),
    "status" -> validatedOptionalText(1),
    "name" -> validatedText(2, 32),
    "label" -> validatedText(2, 32),
    "description" -> validatedText(2, 255)
  ))

  override def validate(): Validation = {
    null
  }

  override def execute(rdh: RequestDataHolder) = rdh match {
    case any =>
      null
  }

  /*
  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("name").isDefined => invalidName
    case e if e.error("label").isDefined => invalidLabel
    case e if e.error("description").isDefined => invalidDescription
    case n => fuck
  }
  */

}
