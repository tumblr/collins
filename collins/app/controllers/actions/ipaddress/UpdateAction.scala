package controllers
package actions
package ipaddress

import models.{Asset, IpAddresses}
import models.shared.IpAddressConfiguration
import util.{ApiTattler, IpAddress, SecuritySpecification}
import validators.{StringUtil, ParamValidation}

import play.api.data.Form
import play.api.data.Forms._
import java.sql.SQLException

case class UpdateAction(
  assetTag: String,
  spec: SecuritySpecification,
  handler: SecureController
) extends SecureAction(spec, handler) with AssetAction with AddressActionHelper with ParamValidation {

  case class ActionDataHolder(
    asset: Asset, oldAddress: Option[Long], address: Option[Long],
    gateway: Option[Long], netmask: Option[Long], pool: Option[String]
  ) extends RequestDataHolder {
    def merge(ipAddress: Option[IpAddresses]): IpAddresses = ipAddress.map { ip =>
      ip.copy(
        address = address.getOrElse(ip.address),
        gateway = gateway.getOrElse(ip.gateway),
        netmask = netmask.getOrElse(ip.netmask),
        pool = pool.map(convertPoolName(_)).getOrElse(ip.pool)
      )
    }.getOrElse {
      if (address.isDefined && gateway.isDefined && netmask.isDefined) {
        val p = convertPoolName(pool.getOrElse(IpAddressConfiguration.DefaultPoolName))
        IpAddresses(asset.getId, gateway.get, address.get, netmask.get, p)
      } else {
        throw new Exception("If creating a new IP the address, gateway and netmask must be specified")
      }
    }
  }

  val optionalIpAddress = validatedOptionalText(7)

  type DataForm = Tuple5[Option[String],Option[String],Option[String],Option[String],Option[String]]
  val dataForm = Form(tuple(
    "old_address" -> optionalIpAddress,
    "address" -> optionalIpAddress,
    "gateway" -> optionalIpAddress,
    "netmask" -> optionalIpAddress,
    "pool" -> validatedOptionalText(1)
  ))

  override def validate(): Validation = withValidAsset(assetTag) { asset =>
    dataForm.bindFromRequest()(request).fold(
      e => Left(RequestDataHolder.error400(fieldError(e))),
      f => normalizeForm(asset, f)
    )
  }

  override def execute(rd: RequestDataHolder) = rd match {
    case adh@ActionDataHolder(asset, old, address, gateway, netmask, pool) =>
      val addressInfo = IpAddresses.findAllByAsset(asset)
                                    .find(_.address == old.getOrElse(0L))
      val newAddress = adh.merge(addressInfo)
      try {
        val (status, success) = update(asset, newAddress)
        Api.statusResponse(success, status)
      } catch {
        case e: SQLException =>
          handleError(RequestDataHolder.error409("Possible duplicate IP address"))
        case e =>
          handleError(
            RequestDataHolder.error500("Unable to update address: %s".format(e.getMessage))
          )
      }
  }

  protected def update(asset: Asset, address: IpAddresses) = address.id match {
    case update if update > 0 => handleUpdate(asset, address)
    case _ => handleCreate(asset, address)
  }

  protected def handleUpdate(asset: Asset, address: IpAddresses) = {
    IpAddresses.update(address) match {
      case 1 =>
        ApiTattler.notice(asset, userOption, "Updated IP address %s".format(address.dottedAddress))
        (Status.Ok, true)
      case _ =>
        ApiTattler.warning(asset, userOption, "Failed to update address %s".format(
          address.dottedAddress
        ))
        (Status.InternalServerError, false)
    }
  }

  protected def handleCreate(asset: Asset, address: IpAddresses) = {
    IpAddresses.create(address).id match {
      case fail if fail <= 0 =>
        ApiTattler.warning(asset, userOption, "Failed to create address %s".format(
          address.dottedAddress
        ))
        (Status.InternalServerError, false)
      case success =>
        ApiTattler.notice(asset, userOption, "Created IP address %s".format(address.dottedAddress))
        (Status.Created, true)
    }
  }

  type NormalizedForm = Either[RequestDataHolder,ActionDataHolder]
  protected def normalizeForm(asset: Asset, form: DataForm): NormalizedForm = {
    val (old,add,gate,net,pool) = form
    val seq = Seq(old,add,gate,net,pool)
    if (!IpAddresses.AddressConfig.isDefined)
      return Left(RequestDataHolder.error500("No address pools have been setup to allocate from"))
    val addressConfig = IpAddresses.AddressConfig.get
    if (addressConfig.strict && pool.isDefined && !addressConfig.hasPool(pool.get))
      return Left(RequestDataHolder.error400("Specified pool is invalid"))
    seq.filter(_.isDefined).map(_.get).foreach { opt =>
      val trimmed = StringUtil.trim(opt)
      if (!trimmed.isDefined)
        return Left(RequestDataHolder.error400("Invalid (empty) value '%s'".format(opt)))
      if (trimmed.get != opt)
        return Left(RequestDataHolder.error400("Invalid (padded) value '%s'".format(opt)))
    }
    Seq(old,add,gate,net).filter(_.isDefined).map(_.get).foreach { opt =>
      if (!IpAddress.toOptLong(opt).isDefined)
        return Left(RequestDataHolder.error400("'%s' is not a valid IP address".format(opt)))
    }
    Right(ActionDataHolder(
      asset, old.map(IpAddress.toLong(_)), add.map(IpAddress.toLong(_)),
      gate.map(IpAddress.toLong(_)), net.map(IpAddress.toLong(_)), pool
    ))
  }

  protected def fieldError(form: Form[DataForm]): String = form match {
    case f if f.error("old_address").isDefined => "old_address not valid"
    case f if f.error("address").isDefined => "invalid address specified"
    case f if f.error("gateway").isDefined => "invalid gateway specified"
    case f if f.error("netmask").isDefined => "invalid netmask specified"
    case f if f.error("pool").isDefined => "invalid pool specified"
    case o => "An unknown error occurred"
  }
}
