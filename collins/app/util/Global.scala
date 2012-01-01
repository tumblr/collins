import play.api._
import play.api.mvc._

import controllers.{ApiResponse, BackgroundProcessor}
import util.{AuthenticationAccessor, AuthenticationProvider, CryptoAccessor, IpmiCommandProcessor}
import util.{BashOutput, HtmlOutput, JsonOutput, OutputType, TextOutput}

object Global extends GlobalSettings with AuthenticationAccessor with CryptoAccessor {
  private[this] val logger = Logger.logger

  private val RequiredConfig = Set(
    "crypto.key", "ipmi.gateway", "ipmi.netmask"
  )

  override def onStart(app: Application) {
    verifyConfiguration(app.configuration)
    // FIXME Run evolutions if needed
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
  override def onStop(app: Application) {
    super.onStop(app)
  }

  override def onError(request: RequestHeader, ex: Throwable): Result = {
    logger.warn("Unhandled exception", ex)
    val debugOutput = Play.maybeApplication.map {
      case app if app.mode == Mode.Dev => true
      case app if app.mode == Mode.Test => true
      case _ => false
    }.getOrElse(true)
    val status = Results.InternalServerError
    val err = if (debugOutput) Some(ex) else None
    OutputType(request) match {
      case Some(BashOutput()) => ApiResponse.bashError("Unhandled exception", status, err)
      case Some(JsonOutput()) => ApiResponse.jsonError("Unhandled exception", status, err)
      case Some(TextOutput()) => ApiResponse.textError("Unhandled exception", status, err)
      case _ => super.onError(request, ex)
    }
  }

  override def onHandlerNotFound(request: RequestHeader): Result = {
    logger.info("Unhandled URI: " + request.uri)
    val msg = "The specified path was invalid: " + request.path
    val status = Results.NotFound
    val err = None
    OutputType(request) match {
      case Some(BashOutput()) => ApiResponse.bashError(msg, status, err)
      case Some(JsonOutput()) => ApiResponse.jsonError(msg, status, err)
      case Some(TextOutput()) => ApiResponse.textError(msg, status, err)
      case t =>
        super.onHandlerNotFound(request)
    }
  }

  override def onBadRequest(request: RequestHeader, error: String): Result = {
    val msg = "Bad Request, parsing failed. " + error
    val status = Results.BadRequest
    val err = None
    logger.info(msg)
    OutputType(request) match {
      case Some(BashOutput()) => ApiResponse.bashError(msg, status, err)
      case Some(JsonOutput()) => ApiResponse.jsonError(msg, status, err)
      case Some(TextOutput()) => ApiResponse.textError(msg, status, err)
      case t =>
        super.onBadRequest(request, error)
    }
  }

  // Make sure we have a valid configuration before we start
  protected def verifyConfiguration(config: Configuration) {
    RequiredConfig.foreach { key =>
      key.split("\\.", 2) match {
        case Array(sub,key) =>
          config.getSub(sub).map { _.get(key).getOrElse(
            throw config.globalError("No %s.%s found in configuration".format(sub, key))
          )}.getOrElse(
            throw config.globalError("No configuration found '" + sub + "'")
          )
        case _ =>
          config.get(key).getOrElse(
            throw config.globalError("No configuration found '" + key + "'")
          )
      }
    }
  }

  // Implements CryptoAccessor
  var cryptoKey: Option[String] = None
  protected def setCryptoKey(key: String) {
    cryptoKey.isDefined match {
      case true => logger.info("cryptoKey already defined")
      case false => cryptoKey = Some(key)
    }
  }
  def getCryptoKey(): String = cryptoKey.get

  // Implements AuthenticationAccessor
  var authentication: Option[AuthenticationProvider] = None
  protected def setAuthentication(auth: AuthenticationProvider) {
    authentication.isDefined match {
      case true => logger.info("authentication provider already defined")
      case false => authentication = Some(auth)
    }
  }
  def getAuthentication() = authentication.get
}
