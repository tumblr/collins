package collins.util.views

import play.api.libs.json.JsArray
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.twirl.api.Html

import collins.models.AssetMeta
import collins.models.AssetMetaValue

import collins.provisioning.ProvisionerConfig
import collins.provisioning.Provisioner

object ProvisionerHelper {
  def profilesAsJson(): Html = {
    if(ProvisionerConfig.enabled) {
      val profiles: Seq[(String,JsValue)] = Provisioner.profiles.map { profile =>
        val role = profile.role
        val fields: Seq[(String,JsValue)] = Seq(
          "requires_primary_role" -> JsBoolean(role.requires_primary_role),
          "requires_pool" -> JsBoolean(role.requires_pool),
          "requires_secondary_role" -> JsBoolean(role.requires_secondary_role)
        ) ++ role.primary_role.map { pr =>
          Seq("primary_role" -> JsString(pr))
        }.getOrElse(Seq()) ++ role.pool.map { p =>
          Seq("pool" -> JsString(p))
        }.getOrElse(Seq()) ++ role.secondary_role.map { sr =>
          Seq("secondary_role" -> JsString(sr))
        }.getOrElse(Seq())
        (profile.identifier -> JsObject(fields))
      }.toSeq
      Html(Json.stringify(JsObject(profiles)))
    } else {
      Html("{}")
    }
  }

  def secondaryRolesAsJson(): Html = {
    metaAsJson("SECONDARY_ROLE")
  }
  def poolsAsJson(): Html = {
    metaAsJson("POOL")
  }
  def primaryRolesAsJson(): Html = {
    metaAsJson("PRIMARY_ROLE")
  }

  protected def metaAsJson(meta: String): Html = {
    val roles: Set[String] = AssetMeta.findByName(meta.toUpperCase).map { meta =>
      AssetMetaValue.findByMeta(meta).map(_.toUpperCase).toSet
    }.getOrElse(Set());
    Html(Json.stringify(JsArray(roles.toList.sorted.map(JsString(_)))))
  }

}
