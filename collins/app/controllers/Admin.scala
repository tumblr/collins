package controllers

import util.{Cache, SecuritySpec}
import views._

object Admin extends SecureWebController {

  implicit val spec = SecuritySpec(true, Seq("infra"))

  def stats = SecureAction { implicit req =>
    Ok(html.admin.stats(Cache.stats()))
  }
}
