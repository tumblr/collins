package controllers
package actions
package asset

import forms._
import validators.ParamValidation

import models.{Asset, AssetLifecycle, State, Status => AssetStatus}
import util.security.SecuritySpecification

import play.api.data.Form
import play.api.data.Forms._

object UpdateStatusAction extends ParamValidation {
  val UpdateForm = Form(tuple(
    "status" -> optional(of[AssetStatus]),
    "state" -> optional(of[State]),
    "reason" -> validatedText(2)
  ))
}

/**
 * Update the status or state of an asset
 *
 * @apigroup Asset
 * @apimethod POST
 * @apiurl /api/asset/:tag/status
 * @apiparam :tag String asset tag
 * @apiparam status Option[Status] new status of asset
 * @apiparam state Option[State] new state of asset
 * @apiparam reason String reason for maintenance
 * @apirespond 200 success
 * @apirespond 400 invalid status or state, missing reason, neither state nor status specified
 * @apirespond 409 state conflicts with status
 * @apirespond 500 error saving status
 * @apiperm controllers.AssetApi.updateAssetStatus
 * @collinsshell {{{
 *  collins-shell asset set_status [STATUS --state=STATE --reason='REASON' --tag=TAG]
 * }}}
 * @curlexample {{{
 *  curl -v -u blake:admin:first --basic \
 *    -d status=Unallocated \
 *    -d state=Running \
 *    -d reason='Ready for action' \
 *    http://localhost:9000/api/asset/TAG/status
 * }}}
 */
case class UpdateStatusAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  import UpdateAction.Messages._
  import UpdateStatusAction._

  case class ActionDataHolder(
    astatus: Option[AssetStatus], state: Option[State], reason: String
  ) extends RequestDataHolder

  override def validate(): Validation = UpdateForm.bindFromRequest()(request).fold(
    err => Left(RequestDataHolder.error400(fieldError(err))),
    form => {
      withValidAsset(assetTag) { asset =>
        val (statusOpt, stateOpt, reason) = form
        if (List(statusOpt,stateOpt).filter(_.isDefined).size == 0) {
          Left(RequestDataHolder.error400(invalidInvocation))
        } else {
          checkStateConflict(asset, statusOpt, stateOpt) match {
            case (Some(status), Some(state)) =>
              Left(RequestDataHolder.error409(stateConflictError(status, state)))
            case _ =>
              Right(ActionDataHolder(statusOpt, stateOpt, reason))
          }
        }
      }
    }
  )

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(status, state, reason) =>
      AssetLifecycle.updateAssetStatus(definedAsset, status, state, reason).fold(
        e => Api.errorResponse("Error updating status", Status.InternalServerError, Some(e)),
        b => Api.statusResponse(b)
      )
  }

  protected def checkStateConflict(
    asset: Asset, statusOpt: Option[AssetStatus], stateOpt: Option[State]
  ): Tuple2[Option[AssetStatus],Option[State]] = {
    val status = statusOpt.getOrElse(asset.getStatus())
    val state = stateOpt.getOrElse(State.findById(asset.state).getOrElse(State.empty))
    if (state.status == State.ANY_STATUS || state.status == status.id) {
      (None, None)
    } else {
      (Some(status), Some(state))
    }
  }

  protected def invalidInvocation =
    rootMessage("controllers.AssetApi.updateStatus.invalidInvocation")

  protected def stateConflictError(status: AssetStatus, state: State) =
    rootMessage("controllers.AssetApi.updateStatus.stateConflict", status.name, state.name)

  protected def fieldError(f: Form[_]) = f match {
    case e if e.error("status").isDefined => invalidStatus
    case e if e.error("state").isDefined => invalidState
    case e if e.error("reason").isDefined =>
      rootMessage("controllers.AssetApi.updateStatus.invalidReason")
    case n => fuck
  }
}
