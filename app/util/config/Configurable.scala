package util
package config

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import play.api.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

trait Configurable extends ConfigAccessor with AppConfig { self =>

  protected val logger = Logger("configurable")

  // By default values are optional, except when they aren't. Methods returning default values do
  // not use this. Methods without defaults will take a ConfigRequirement as an implicit
  // argument, or fall back to this
  implicit val configValue: ConfigRequirement = ConfigValue.Optional

  // Namespace owned by implementor
  val namespace: String

  // A reference configuration for sanity checking and defaults
  val referenceConfigFilename: String

  // Called when the underlying configuration changes in any way
  protected def validateConfig()

  override def ns = Some(namespace)

  // This is only assigned to during delayedInit call, after constructor code
  private var referenceConfig: Option[TypesafeConfiguration] = None

  // Used with plugins that need to use configs before the system is initialized
  def pluginInitialize(config: PlayConfiguration) {
    if (once) {
      mergeReferenceAndSave(config.underlying, true)
    }
  }

  // Setup referenceConfig and register self with Registry

  // Will be called from Registry.validate, should not be called directly except maybe in tests
  def initialize() {
    mergeReferenceAndSave(appConfig().underlying)
  }

  protected def refFilename = "reference/%s".format(referenceConfigFilename)
  protected val alreadyRun = new AtomicBoolean(false)

  def once(): Boolean = {
    if (!alreadyRun.compareAndSet(false, true)) {
      return false
    }
    logger.debug("ReferenceConfig setup for %s on %s".format(getClass.getName, refFilename))
    try {
      val rc = ConfigFactory.parseResourcesAnySyntax(refFilename).resolve
      if (rc.isEmpty) {
        throw new Exception("Got back empty (not found) configuration for %s:%s".format(
          namespace, refFilename
        ))
      }
      referenceConfig = Some(rc)
    } catch {
      case e =>
        val msg = "Reference configuration %s not found or invalid: %s".format(
          refFilename, e.getMessage
        )
        SystemTattler.safeError(msg)
        logger.error(msg, e)
        referenceConfig = None
    }
    Registry.add(namespace, this)
    true
  }

  final protected[config] def onChange(newConfig: TypesafeConfiguration) {
    try {
      mergeReferenceAndSave(newConfig)
      afterChange()
    } catch {
      case e =>
        logger.warn("Exception handling file (%s) change: %s".format(
          Option(newConfig.origin.filename).getOrElse("unknown"), e.getMessage
        ))
    }
  }

  protected def afterChange() {
  }

  protected def mergeReferenceAndSave(config: TypesafeConfiguration, skipValidation: Boolean = false) {
    try {
      logger.trace("Trying to merge reference config and save")
      val savedConfig = underlying
      val mergedConfig = referenceConfig.map { rc =>
        val merged = config.withFallback(rc).resolve()
        logger.trace("Reference config: " + rc.toString)
        logger.trace("Merged config: " + merged.toString)
        logger.debug("Checking namespace " + namespace)
        merged.checkValid(rc, namespace)
        merged.getConfig(namespace)
      }.getOrElse(config.resolve)
      self.underlying = Some(mergedConfig)
      try {
        if (!skipValidation) {
          logger.debug("Validating configuration for %s".format(getClass.getName))
          validateConfig()
          logger.trace("Validation successful for configuration for %s".format(getClass.getName))
        }
      } catch {
        case e =>
          val msg = "Error validating configuration for %s: %s".format(
            getClass.getName, e.getMessage
          )
          SystemTattler.safeError(msg)
          logger.error(msg, e)
          self.underlying = savedConfig
          throw e
      }
    } catch {
      case e =>
        val msg = "Exception in mergeReferenceAndSave: %s".format(e.getMessage)
        SystemTattler.safeError(msg)
        logger.error(msg, e)
        throw e
    }
  }
}
