package util
package config

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import play.api.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TODO
 *  - Deprecate old Config class
 *  - Deprecate old Feature class
 */
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

  override def ns = Some(namespace)

  // Called when the underlying configuration changes in any way
  protected def validateConfig()

  // This is only assigned to during delayedInit call, after constructor code
  private var referenceConfig: Option[TypesafeConfiguration] = None

  // Setup referenceConfig and register self with Registry

  // Will be called from Registry.validate, should not be called directly except maybe in tests
  def initialize() {
    mergeReferenceAndSave(appConfig().underlying)
  }

  protected def refFilename = "reference/%s".format(referenceConfigFilename)
  protected val alreadyRun = new AtomicBoolean(false)

  def once() {
    if (!alreadyRun.compareAndSet(false, true)) {
      return
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
        logger.error("Reference configuration %s not found or invalid: %s".format(
          refFilename, e.getMessage
        ), e)
        referenceConfig = None
    }
    Registry.add(namespace, this)
  }

  protected[config] def onChange(newConfig: TypesafeConfiguration) {
    try {
      mergeReferenceAndSave(newConfig)
    } catch {
      case e =>
        logger.warn("Exception handling file (%s) change: %s".format(
          Option(newConfig.origin.filename).getOrElse("unknown"), e.getMessage
        ))
    }
  }

  protected def mergeReferenceAndSave(config: TypesafeConfiguration) {
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
        logger.debug("Validating configuration for %s".format(getClass.getName))
        validateConfig()
      } catch {
        case e =>
          logger.error("Error validating configuration for %s: %s".format(
            getClass.getName, e.getMessage))
          self.underlying = savedConfig
          throw e
      }
    } catch {
      case e =>
        logger.error("Exception in mergeReferenceAndSave %s".format(e.getMessage), e)
        throw e
    }
  }
}
