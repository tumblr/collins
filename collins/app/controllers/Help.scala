package controllers

import play.api.mvc._

import util.SecuritySpec
import views._

abstract class Help(val id: Int)
object Help {
  val Default = 0
  case class DefaultHelp() extends Help(0)
  case class IpmiLight() extends Help(1)
  case class IpmiError() extends Help(2)
  def apply(id: Int): Help = id match {
    case 0 => DefaultHelp()
    case 1 => IpmiLight()
    case 2 => IpmiError()
    case n => throw new IllegalArgumentException("Specified help id is unknown")
  }
}

trait HelpPage extends Controller {
  this: SecureController =>

  import Help._

  implicit val spec = SecuritySpec(isSecure = false, Nil)

  def index(htype: Int) = SecureAction { implicit req =>
    val help = Help(htype)
    Ok(html.help(help))
  }

}
