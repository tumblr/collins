import play.api._
import play.api.mvc._
import collins.database.DatabasePlugin

import controllers.ApiResponse
import util.{CryptoAccessor, Stats}
import util.{BashOutput, HtmlOutput, JsonOutput, OutputType, TextOutput}
import util.config.CryptoConfig
import util.security.{AuthenticationAccessor, AuthenticationProvider, AuthenticationProviderConfig}

object Global extends GlobalSettings with AuthenticationAccessor with CryptoAccessor {
  private[this] val logger = Logger.logger

  override def onStart(app: Application) {
    val auth = AuthenticationProvider.get(AuthenticationProviderConfig.authType)
    val key = CryptoConfig.key
    setAuthentication(auth)
    setCryptoKey(key)
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    val response = if (request.path.startsWith("/api")) {
      Stats.apiRequest {
        super.onRouteRequest(request)
      }
    } else if (!request.path.startsWith("/assets/")) {
      Stats.webRequest {
        super.onRouteRequest(request)
      }
    } else {
      super.onRouteRequest(request)
    }

    Play.maybeApplication.flatMap{_.plugin[DatabasePlugin]}.filter{_.enabled}.foreach{_.closeConnection}
    response
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
  def getAuthentication() = {
    val authen = authentication.get
    if (AuthenticationProviderConfig.authType != authen.authType) {
      try {
        val auth = AuthenticationProvider.get(AuthenticationProviderConfig.authType)
        authentication = Some(auth)
      } catch {
        case e =>
      }
    }
    authen
  }

}
