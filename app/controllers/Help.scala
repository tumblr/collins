package controllers

import play.api.mvc._

import util.MessageHelper
import views._

import collection.immutable.DefaultMap

object Help extends MessageHelper("help") with DefaultMap[String,String] {

  val Default = ""
  val IpmiError = "ipmi.error"
  val IpmiNoLight = "ipmi.nolight"
  val IpmiUnreachable = "ipmi.unreachable"
  val PowerManagementDisabled = "powermanagement.disabled"

  val underlying = Map(
    Default -> "",
    IpmiError -> "An IPMI error occurred. Error was {0}",
    IpmiNoLight -> "No IPMI light",
    IpmiUnreachable -> "An IPMI error occurred and is also unreachable. Original error was {0}.",
    PowerManagementDisabled -> "Power management is disabled"
  )

  override def get(key: String) = underlying.get(key)
  def getMessage(key: String, args: String*) = contains(key) match {
    case true =>
      Some(messageWithDefault(key, apply(key), args:_*))
    case false =>
      None
  }
  override def iterator = underlying.iterator
}

trait HelpPage extends Controller {
  this: SecureController =>

  def index(hkey: String) = SecureAction { implicit req =>
    if (Help.contains(hkey))
      Ok(html.help(hkey))
    else
      Ok(html.help(Help.Default))
  }(Permissions.Help.Index)

}
