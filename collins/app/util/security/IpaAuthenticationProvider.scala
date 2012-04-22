package util

import play.api.Configuration

class IpaAuthenticationProvider(config: Configuration) extends LdapAuthenticationProvider(config) {
  override protected def groupQuery(username: String): String = {
    "(&(cn=*)(member=%s))".format(getSecurityPrincipal(username))
  }
}


