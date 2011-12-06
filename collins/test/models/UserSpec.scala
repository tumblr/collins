package models

import util.AuthenticationProvider
import play.api._
import org.specs2.mutable._

object UserSpec extends Specification {

  "A User" should {
    "handle authentication" in {
      "console mode" >> {
        val provider = AuthenticationProvider.Default
        User.authenticate("blake", "admin:first", Some(provider)) must beSome[User]
      }
      "api mode" >> {
        val configString = """
        authentication.type=ldap
        authentication.host=admin02.jfk01.tumblr.net
        """
        val config = Configuration.load(configString)
        val provider = AuthenticationProvider.get("ldap", config.getSub("authentication"))
      }.pendingUntilFixed
    }
  }
}
