package controllers
package actions
package asset

import util.Provisioner
import util.security.SecuritySpecification
import collins.provisioning.ProvisionerProfile
import play.api.libs.json._

case class GetProvisioningProfilesAction(
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction {

  case class ActionDataHolder(profiles: Set[ProvisionerProfile]) extends RequestDataHolder

  override def validate(): Validation = Provisioner.plugin match {
    case None =>
      Left(RequestDataHolder.error501("Provisioner plugin not enabled"))
    case Some(plugin) =>
      Right(ActionDataHolder(plugin.profiles))
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case ActionDataHolder(profiles) =>
      val obj = JsObject(Seq("PROFILES" -> formatProfiles(profiles)))
      ResponseData(Status.Ok, obj)
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
