package controllers

import models.{Asset, IpAddresses}
import util.{IpAddress, UserTattler, SecuritySpec}
import play.api.http.{Status => StatusValues}
import play.api.mvc._
import play.api.libs.json._
import play.api.data._
import play.api.data.format.Formats._
import java.sql.SQLException

trait IpAddressApi {
  this: Api with SecureController =>

  case class IpAddressForm(address: Option[String], gateway: Option[String], netmask: Option[String], pool: Option[String]) {
    def merge(asset: Asset, ipaddress: Option[IpAddresses]): IpAddresses = {
      ipaddress.map { ip =>
        ip.copy(
          address = address.map(IpAddress.toLong(_)).getOrElse(ip.address),
          gateway = gateway.map(IpAddress.toLong(_)).getOrElse(ip.gateway),
          netmask = netmask.map(IpAddress.toLong(_)).getOrElse(ip.netmask),
          pool = pool.map(getPoolName(_)).getOrElse(ip.pool)
        )
      }.getOrElse {
        if (address.isDefined && gateway.isDefined && netmask.isDefined) {
          val a = IpAddress.toLong(address.get)
          val g = IpAddress.toLong(gateway.get)
          val n = IpAddress.toLong(netmask.get)
          val p = getPoolName(pool.getOrElse("default"))
          IpAddresses(asset.getId, g, a, n, p)
        } else {
          throw new Exception("If creating a new IP the address, gateway and netmask must be specified")
        }
      }
    }
  }
  val UpdateForm = Form(
    of(IpAddressForm.apply _, IpAddressForm.unapply _)(
      "address" -> optional(text(7)),
      "gateway" -> optional(text(7)),
      "netmask" -> optional(text(7)),
      "pool" -> optional(text)
    )
  )

  // PUT /api/asset/:tag/address
  def allocateAddress(tag: String) = Authenticated { user => Action { implicit req =>
    val count: Int = getInt("count", 1) match {
      case useDefault if useDefault < 1 || useDefault > 10 => 1
      case n => n
    }
    val pool: String = getPoolName(getString("pool", ""))
    withTag(tag) { asset =>
      try {
        val created = IpAddresses.createForAsset(asset, count, Some(pool))
        UserTattler.notice(asset, user, "Created %d IP addresses".format(created.size))
        addressesToJson(created, Results.Created)
      } catch {
        case e => Api.getErrorMessage("Invalid pool specified")
      }
    }
  }}(SecuritySpec(true, Seq("infra")))

  // GET /api/asset/with/address/:address
  def assetFromAddress(address: String) = SecureAction { implicit req =>
    formatResponseData(
      IpAddresses.findByAddress(address).map(s =>
        ResponseData(Results.Ok, JsObject(s.forJsonObject()))
      ).getOrElse(Api.getErrorMessage("No assets found with specified address", Results.NotFound))
    )
  }(SecuritySpec(true, Nil))

  // GET /api/assets/with/addresses/in/:pool
  def assetsFromPool(pool: String) = SecureAction { implicit req =>
    formatResponseData(
      IpAddresses.findInPool(getPoolName(pool)) match {
        case Nil =>
          Api.getErrorMessage("No such pool or no assets in pool", Results.NotFound)
        case list =>
          val jsList = list.map { e =>
            JsObject(Asset.findById(e.asset_id).get.forJsonObject)
          }.toList
          ResponseData(Results.Ok, JsObject(Seq("ASSETS" -> JsArray(jsList))))
      }
    )
  }(SecuritySpec(true, Nil))

  // GET /api/asset/:tag/addresses
  def getForAsset(tag: String) = SecureAction { implicit req =>
    withTag(tag) { asset =>
      addressesToJson(IpAddresses.findAllByAsset(asset))
    }
  }(SecuritySpec(true, Nil))

  // POST /api/asset/:tag/address
  def updateAddress(tag: String) = Authenticated { user => Action { implicit req =>
    val old_address = getString("old_address", "")
    withTag(tag) { asset =>
      UpdateForm.bindFromRequest.fold(
        e => Api.getErrorMessage("Error updating address: %s".format(e.errors.map(_.message).mkString(","))),
        ipAddressForm => {
          val addressInfo = IpAddresses.findAllByAsset(asset).find(_.dottedAddress == old_address.trim)
          try {
            val newAddress = ipAddressForm.merge(asset, addressInfo)
            val (status, success) = newAddress.id match {
              case update if update > 0 =>
                UserTattler.notice(asset, user, "Updated IP address %s".format(newAddress.dottedAddress))
                (Results.Ok, IpAddresses.update(newAddress) == 1)
              case _ =>
                UserTattler.notice(asset, user, "Created IP address %s".format(newAddress.dottedAddress))
                (Results.Created, IpAddresses.create(newAddress).id > 0)
            }
            Api.statusResponse(success, status)
          } catch {
            case e: SQLException =>
              Api.getErrorMessage("Possible duplicate IP Address",
                Results.Status(StatusValues.CONFLICT))
            case e => Api.getErrorMessage("Unable to update address: %s".format(e.getMessage))
          }
        }
      )
    }
  }}(SecuritySpec(true, Seq("infra")))

  // DELETE /api/asset/:tag/addresses
  def purgeAddresses(tag: String) = Authenticated { user => Action { implicit req =>
    val pool = Option(getString("pool", "garbage")).filter(_ != "garbage")
    withTag(tag) { asset =>
      val deleted = IpAddresses.deleteByAssetAndPool(asset, pool)
      UserTattler.notice(asset, user, "Deleted %d IP addresses".format(deleted))
      ResponseData(Results.Ok, JsObject(Seq("DELETED" -> JsNumber(deleted))))
    }
  }}(SecuritySpec(true, Seq("infra")))

  protected def addressesToJson(addresses: Seq[IpAddresses], status: Results.Status = Results.Ok) =
    ResponseData(status, JsObject(Seq("ADDRESSES" -> JsArray(
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

  protected def getString(k: String, default: String)(implicit req: Request[AnyContent]): String =
    Form(k -> optional(of[String])).bindFromRequest.fold(
      e => default,
      s => s.getOrElse(default)
    )

  protected def getPoolName(p: String) = if (p.toLowerCase == "default") {
    ""
  } else {
    p.toLowerCase
  }
}
