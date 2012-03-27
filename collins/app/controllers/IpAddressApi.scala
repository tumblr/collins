package controllers

import models.{Asset, IpAddresses}
import util.UserTattler
import play.api.mvc._
import play.api.libs.json._
import play.api.data._
import play.api.data.format.Formats._

trait IpAddressApi {
  this: Api with SecureController =>

  def allocateAddress(tag: String) = Authenticated { user => Action { implicit req =>
    val count: Int = getInt("count", 1) match {
      case useDefault if useDefault < 1 || useDefault > 10 => 1
      case n => n
    }
    withTag(tag) { asset =>
      val created = (0 until count).map(_ =>
        IpAddresses.createForAsset(asset)
      )
      UserTattler.notice(asset, user, "Created %d IP addresses".format(created.size))
      addressesToJson(created)
    }
  }}

  // GET /api/asset/with/address/:address
  def assetFromAddress(address: String) = SecureAction { implicit req =>
    formatResponseData(
      IpAddresses.findByAddress(address).map(s =>
        ResponseData(Results.Ok, JsObject(s.forJsonObject()))
      ).getOrElse(Api.getErrorMessage("No assets found with specified address", Results.NotFound))
    )
  }

  // GET /api/asset/:tag/addresses
  def getForAsset(tag: String) = SecureAction { implicit req =>
    withTag(tag) { asset =>
      addressesToJson(IpAddresses.findAllByAsset(asset))
    }
  }

  // DELETE /api/asset/:tag/addresses
  def purgeAddresses(tag: String) = Authenticated { user => Action { implicit req =>
    withTag(tag) { asset =>
      val deleted = IpAddresses.deleteByAsset(asset)
      UserTattler.notice(asset, user, "Deleted %d IP addresses".format(deleted))
      ResponseData(Results.Ok, JsObject(Seq("DELETED" -> JsNumber(deleted))))
    }
  }}

  protected def addressesToJson(addresses: Seq[IpAddresses]) =
    ResponseData(Results.Ok, JsObject(Seq("ADDRESSES" -> JsArray(
      addresses.toList.map(j => JsObject(j.forJsonObject()))
    ))))

  protected def withTag[T <: ResponseData](tag: String)(f: Asset => T)(implicit req: Request[AnyContent]) = formatResponseData(
    Asset.findByTag(tag).map(f(_)).getOrElse(
      Api.getErrorMessage("Invalid asset tag specified", Results.NotFound)
    )
  )

  protected def getInt(k: String, default: Int)(implicit req: Request[AnyContent]): Int =
    Form(k -> optional(of[Int])).bindFromRequest.fold(
      e => default,
      s => s.getOrElse(default)
    )

}
