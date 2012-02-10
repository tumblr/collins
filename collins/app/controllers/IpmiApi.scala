package controllers

import models.{Asset, IpmiInfo, Model}
import util.{IpAddress, SecuritySpec}

import anorm.{Id, NotAssigned}
import play.api.data._
import play.api.http.{Status => StatusValues}
import play.api.libs.json.{JsBoolean, JsObject}
import play.api.mvc.Results
import java.sql.SQLException

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
        IpmiInfo(NotAssigned, Id(asset.getId), username.get, p, g, a, n)
      }
    }
  }
  val FORM = Form(
    of(IpmiForm.apply _, IpmiForm.unapply _)(
      "username" -> optional(text(1)),
      "password" -> optional(text(8)),
      "address" -> optional(text(7)),
      "gateway" -> optional(text(7)),
      "netmask" -> optional(text(7))
    )
  )

  def updateIpmi(tag: String) = SecureAction { implicit req =>
    Api.withAssetFromTag(tag) { asset =>
      val ipmiInfo = IpmiInfo.findByAsset(asset)
      FORM.bindFromRequest.fold(
        hasErrors => {
          val error = hasErrors.errors.map { _.message }.mkString(", ")
          Left(Api.getErrorMessage("Data submission error: %s".format(error)))
        },
        ipmiForm => {
          try {
            val newInfo = ipmiForm.merge(asset, ipmiInfo)
            val (status, success) = Model.withTransaction { implicit con =>
              newInfo.id match {
                case update if update.isDefined =>
                  (Results.Ok, IpmiInfo.update(newInfo) == 1)
                case _ =>
                  (Results.Created, IpmiInfo.create(newInfo).id.isDefined)
              }
            }
            Right(ResponseData(status, JsObject(Seq("SUCCESS" -> JsBoolean(success)))))
          } catch {
            case e: SQLException =>
              Left(Api.getErrorMessage("Possible duplicate IPMI Address",
                Results.Status(StatusValues.CONFLICT)))
            case e =>
              Left(Api.getErrorMessage("Incomplete form submission: %s".format(e.getMessage)))
          }
        }
      )
    }.fold(
      err => formatResponseData(err),
      suc => formatResponseData(suc)
    )
  }(SecuritySpec(true, Seq("infra")))

}
