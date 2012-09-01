package util
package config

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import play.api.{Configuration, Logger}
import java.io.File

/**
 * TODO
 *  - Deprecate old Config class
 *  - Deprecate old Feature class
 */
trait Configurable extends DelayedInit with ConfigurationAccessor with ApplicationConfiguration { self =>

  def apply() {} // noop, here for dynamic loading

  // By default values are optional, except when they aren't. Methods returning default values do
  // not use this. Methods without defaults will take a ConfigurationRequirement as an implicit
  // argument, or fall back to this
  implicit val configValue: ConfigurationRequirement = ConfigValue.Optional

  // Namespace owned by implementor
  val namespace: String
  // A reference configuration for sanity checking and defaults
  val referenceConfigFilename: String

  protected val logger = Logger("configurable")

  // Called when the underlying configuration changes in any way
  protected def validateConfig()

  // This is only assigned to during delayedInit call, after constructor code
  private var referenceConfig: Option[TypesafeConfig] = None

  // Setup referenceConfig and register self with Registry
  override def delayedInit(x: => Unit) {
    logger.trace("Running constructor")
    x
    logger.trace("Ran constructor code")
    try {
      referenceConfig = Some(ConfigFactory.parseResourcesAnySyntax(referenceConfigFilename).resolve)
    } catch {
      case e =>
        logger.error("Reference configuration %s not found or invalid: %s".format(
          referenceConfigFilename, e.getMessage
        ), e)
        referenceConfig = None
    }
    Registry.add(namespace, this)
  }

  // Will be called from Registry.validate, should not be called directly except maybe in tests
  def initialize() {
    mergeReferenceAndSave(appConfig().underlying)
  }

  def onChange(newConfig: TypesafeConfig) {
    try {
      mergeReferenceAndSave(newConfig)
    } catch {
      case e =>
        logger.warn("Exception handling file (%s) change: %s".format(
          Option(newConfig.origin.filename).getOrElse("unknown"), e.getMessage
        ))
    }
  }

  protected def mergeReferenceAndSave(config: TypesafeConfig) {
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
        logger.info("Validationg configuration for %s".format(getClass.getName))
        validateConfig()
      } catch {
        case e =>
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
