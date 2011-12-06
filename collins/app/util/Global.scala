import play.api._

import util.{AuthenticationProvider, AuthenticationAccessor}

object Global extends GlobalSettings with AuthenticationAccessor {
  override def onStart(app: Application) {
    val auth = app.configuration.getSub("authentication") match {
      case None => AuthenticationProvider.Default
      case Some(config) => config.getString("type", Some(AuthenticationProvider.Types)) match {
        case None => AuthenticationProvider.Default
        case Some(t) => AuthenticationProvider.get(t, config)
      }
    }
    setAuthentication(auth)
  }

  var authentication: Option[AuthenticationProvider] = None
  protected def setAuthentication(auth: AuthenticationProvider) {
    authentication.isDefined match {
      case true => throw new IllegalStateException("Authentication already specified")
      case false => authentication = Some(auth)
    }
  }
  def getAuthentication() = authentication.get
}
