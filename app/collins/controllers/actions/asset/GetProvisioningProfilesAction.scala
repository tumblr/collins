package collins.controllers.actions.asset

import scala.concurrent.Future
import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsNull
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import collins.controllers.ResponseData
import collins.controllers.SecureController
import collins.controllers.actions.AssetAction
import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.provisioning.ProvisionerProfile
import collins.provisioning.Provisioner
import collins.util.security.SecuritySpecification
import collins.provisioning.ProvisionerConfig

case class GetProvisioningProfilesAction(
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(profiles: Set[ProvisionerProfile]) extends RequestDataHolder

  override def validate(): Validation = ProvisionerConfig.enabled match {
    case false =>
      Left(RequestDataHolder.error501("Provisioning is not not enabled"))
    case true =>
      Right(ActionDataHolder(Provisioner.profiles))
  }

  override def execute(rd: RequestDataHolder) = Future { 
    rd match {
      case ActionDataHolder(profiles) =>
        val obj = JsObject(Seq("PROFILES" -> formatProfiles(profiles)))
        ResponseData(Status.Ok, obj)
    }
  }

  protected def formatProfiles(profiles: Set[ProvisionerProfile]) = JsArray(
    profiles.toList.map { profile =>
      val role = profile.role
      val seq = Seq(
        "PROFILE"                 -> JsString(profile.identifier),
        "LABEL"                   -> JsString(profile.label),
        "PREFIX"                  -> JsString(profile.prefix),
        "SUFFIX_ALLOWED"          -> JsBoolean(profile.allow_suffix),
        "PRIMARY_ROLE"            -> stringOrNull(role.primary_role),
        "REQUIRES_PRIMARY_ROLE"   -> JsBoolean(role.requires_primary_role),
        "POOL"                    -> stringOrNull(role.pool),
        "REQUIRES_POOL"           -> JsBoolean(role.requires_pool),
        "SECONDARY_ROLE"          -> stringOrNull(role.secondary_role),
        "REQUIRES_SECONDARY_ROLE" -> JsBoolean(role.requires_secondary_role)
      )
      JsObject(seq)
    }
  )

  private def stringOrNull(s: Option[String]): JsValue = s.map(JsString(_)).getOrElse(JsNull)
}
