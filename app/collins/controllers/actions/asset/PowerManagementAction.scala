package collins.controllers.actions.asset

import play.api.data.Form
import play.api.data.Forms.of
import play.api.data.Forms.single

import collins.controllers.SecureController
import collins.controllers.forms.powerFormat
import collins.power.PowerAction
import collins.util.security.SecuritySpecification

case class PowerManagementAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends PowerManagementActionHelper(assetTag, spec, handler) {

  override lazy val powerAction: Option[PowerAction] = Form(single(
    "action" -> of[PowerAction]
  )).bindFromRequest()(request).fold(
    err => None,
    suc => Some(suc)
  )
}
