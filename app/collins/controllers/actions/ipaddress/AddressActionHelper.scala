package collins.controllers.actions.ipaddress

import play.api.libs.json.JsObject
import play.api.libs.json.Json

import collins.controllers.actions.RequestDataHolder
import collins.controllers.actions.SecureAction
import collins.models.IpAddresses
import collins.models.conversions.IpAddressFormat
import collins.models.shared.IpAddressConfig
import collins.util.IpAddress

trait AddressActionHelper { self: SecureAction =>
  class AddressDecorator(addresses: Seq[IpAddresses]) {
    import collins.models.conversions._
    def toJson = JsObject(Seq("ADDRESSES" -> Json.toJson(addresses)))
  }

  implicit def seq2json(addresses: Seq[IpAddresses]): AddressDecorator =
    new AddressDecorator(addresses)

  def withValidAddress(a: String)(f: String => Validation): Validation =
    IpAddress.toOptLong(a) match {
      case Some(_) => f(a)
      case None => Left(RequestDataHolder.error400(
        "Invalid IP address '%s' specified".format(a)
      ))
    }

  def convertPoolName(name: String) = name match {
    case empty if empty.isEmpty =>
      IpAddressConfig.DefaultPoolName
    case other => other.toUpperCase
  }
}
