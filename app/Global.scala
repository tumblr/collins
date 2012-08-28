import play.api._
import play.api.mvc._

import controllers.ApiResponse
import models.{IpAddresses, Model}
import util.{AppConfig, CryptoAccessor, Stats}
import util.{BashOutput, HtmlOutput, JsonOutput, OutputType, TextOutput}
import util.config.Registry
import util.security.{AuthenticationAccessor, AuthenticationProvider}
import util.power.PowerConfiguration
import util.plugins.solr.Solr
import java.io.File

object Global extends GlobalSettings with AuthenticationAccessor with CryptoAccessor {
  private[this] val logger = Logger.logger

  private val RequiredConfig = Set(
    "crypto.key", "ipmi.network"
  )

  override def onStart(app: Application) {
    setupLogging(app)
    verifyConfiguration(app)
    val auth = app.configuration.getConfig("authentication") match {
      case None => AuthenticationProvider.Default
      case Some(config) => config.getString("type", Some(AuthenticationProvider.Types)) match {
        case None => AuthenticationProvider.Default
        case Some(t) => AuthenticationProvider.get(t, config)
      }
    }
    val key = app.configuration.getConfig("crypto") match {
      case None => throw new RuntimeException("No crypto.key specified in config")
      case Some(config) => config.getString("key") match {
        case None => throw new RuntimeException("No crypto.key specified in config")
        case Some(k) => k
      }
    }
    Model.initialize()
    setAuthentication(auth)
    setCryptoKey(key)
    checkRuntime(app.configuration)
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    if (request.path.startsWith("/api")) {
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
  }

  override def onStop(app: Application) {
    logger.info("Stopping application")
    super.onStop(app)
    Model.shutdown()
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

  protected def checkRuntime(config: Configuration) {
    PowerConfiguration.validate()
    IpAddresses.AddressConfig
    AppConfig.ipmi
  }

  // Make sure we have a valid configuration before we start
  protected def verifyConfiguration(app: Application) {
    val config = app.configuration
    Registry.initializeAll(app)
    Registry.validate
    RequiredConfig.foreach { key =>
      key.split("\\.", 2) match {
        case Array(sub,key) =>
          config.getConfig(sub).map { _.getString(key).getOrElse(
            throw config.globalError("No %s.%s found in configuration".format(sub, key))
          )}.getOrElse(
            throw config.globalError("No configuration found '" + sub + "'")
          )
        case _ =>
          config.getConfig(key).getOrElse(
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

  protected def setupLogging(app: Application) {
    if (Play.isDev(app)) {
      Option(this.getClass.getClassLoader.getResource("dev_logger.xml"))
        .map(_.getFile())
        .foreach { file =>
          System.setProperty("logger.file", file)
          Logger.init(new File("."))
        }
    }
  }
}
