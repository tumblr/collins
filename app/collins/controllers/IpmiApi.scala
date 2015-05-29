package collins.controllers

import java.sql.SQLException

import scala.Left
import scala.Right

import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.data.Forms.optional
import play.api.data.Forms.text
import play.api.http.{Status => StatusValues}
import play.api.libs.json.JsBoolean
import play.api.libs.json.JsObject
import play.api.mvc.Results

import collins.models.Asset
import collins.models.IpmiInfo
import collins.util.IpAddress

trait IpmiApi {
  this: Api with SecureController =>

  case class IpmiForm(username: Option[String], password: Option[String], address: Option[String], gateway: Option[String], netmask: Option[String]) {
    def merge(asset: Asset, ipmi: Option[IpmiInfo]): IpmiInfo = {
      ipmi.map { info =>
        val iu: IpmiInfo = username.map(u => info.copy(username = u)).getOrElse(info)
        val pu: IpmiInfo = password.map(p => iu.copy(password = IpmiInfo.encryptPassword(p))).getOrElse(iu)
        val au: IpmiInfo = address.map(a => pu.copy(address = IpAddress.toLong(a))).getOrElse(pu)
        val gu: IpmiInfo = gateway.map(g => au.copy(gateway = IpAddress.toLong(g))).getOrElse(au)
        netmask.map(n => gu.copy(netmask = IpAddress.toLong(n))).getOrElse(gu)
      }.getOrElse {
        val a = IpAddress.toLong(address.get)
        val g = IpAddress.toLong(gateway.get)
        val n = IpAddress.toLong(netmask.get)
        val p = IpmiInfo.encryptPassword(password.get)
        IpmiInfo(asset.getId, username.get, p, g, a, n)
      }
    }
  }
  val IPMI_FORM = Form(
    mapping(
      "username" -> optional(text(1)),
      "password" -> optional(text(minLength=5, maxLength=16)),
      "address" -> optional(text(7)),
      "gateway" -> optional(text(7)),
      "netmask" -> optional(text(7))
    )(IpmiForm.apply)(IpmiForm.unapply)
  )

  def updateIpmi(tag: String) = SecureAction { implicit req =>
    Api.withAssetFromTag(tag) { asset =>
      val ipmiInfo = IpmiInfo.findByAsset(asset)
      IPMI_FORM.bindFromRequest.fold(
        hasErrors => {
          val error = hasErrors.errors.map { _.message }.mkString(", ")
          Left(Api.getErrorMessage("Data submission error: %s".format(error)))
        },
        ipmiForm => {
          try {
            val newInfo = ipmiForm.merge(asset, ipmiInfo)
            val (status, success) = newInfo.id match {
              case update if update > 0 =>
                IpmiInfo.update(newInfo) match {
                  case 0 => (Results.Conflict, false)
                  case _ => (Results.Ok, true)
                }
              case _ =>
                (Results.Created, IpmiInfo.create(newInfo).id > 0)
            }
            if (status == Results.Conflict) {
              Left(Api.getErrorMessage("Unable to update IPMI information",status))
            } else {
              Right(ResponseData(status, JsObject(Seq("SUCCESS" -> JsBoolean(success)))))
            }
          } catch {
            case e: SQLException =>
              Left(Api.getErrorMessage("Possible duplicate IPMI Address",
                Results.Status(StatusValues.CONFLICT)))
            case e: Throwable =>
              Left(Api.getErrorMessage("Incomplete form submission: %s".format(e.getMessage)))
          }
        }
      )
    }.fold(
      err => formatResponseData(err),
      suc => formatResponseData(suc)
    )
  }(Permissions.IpmiApi.UpdateIpmi)

}
