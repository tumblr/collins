import play.api._

import util.{AuthenticationProvider, AuthenticationAccessor, CryptoAccessor}

object Global extends GlobalSettings with AuthenticationAccessor with CryptoAccessor {
  private[this] val logger = Logger.logger

  override def onStart(app: Application) {
    val auth = app.configuration.getSub("authentication") match {
      case None => AuthenticationProvider.Default
      case Some(config) => config.getString("type", Some(AuthenticationProvider.Types)) match {
        case None => AuthenticationProvider.Default
        case Some(t) => AuthenticationProvider.get(t, config)
      }
    }
    val key = app.configuration.getSub("crypto") match {
      case None => throw new RuntimeException("No crypto.key specified in config")
      case Some(config) => config.getString("key") match {
        case None => throw new RuntimeException("No crypto.key specified in config")
        case Some(k) => k
      }
    }
    setAuthentication(auth)
    setCryptoKey(key)
  }

  var cryptoKey: Option[String] = None
  protected def setCryptoKey(key: String) {
    cryptoKey.isDefined match {
      case true => logger.info("cryptoKey already defined")
      case false => cryptoKey = Some(key)
    }
  }
  def getCryptoKey(): String = cryptoKey.get

  var authentication: Option[AuthenticationProvider] = None
  protected def setAuthentication(auth: AuthenticationProvider) {
    authentication.isDefined match {
      case true => logger.info("authentication provider already defined")
      case false => authentication = Some(auth)
    }
  }
  def getAuthentication() = authentication.get
}
