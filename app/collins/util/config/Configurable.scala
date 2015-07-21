package collins.util.config

import java.util.concurrent.atomic.AtomicBoolean

import play.api.Logger

import com.typesafe.config.ConfigFactory

import collins.util.InternalTattler

/**
 * All derived types of this class must be 'Object' types (singletons)
 * 
 * The Registry manages the creation and lifecycle of configurable
 * instances. In doing so it make assumptions, one of which is that
 * the config is of type Object. This is captured in the logic for
 * invocation of the 'once' method, it is invoked on the class 
 * and not on the instance.
 * 
 * Configurable ensures that Collins configuration types are dynamically
 * loaded onChange without requiring application restart.
 * 
 */
trait Configurable extends ConfigAccessor with AppConfig { self =>

  protected val logger = Logger(getClass)

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

  // Will be called from Registry.validate, should not be called directly except maybe in tests
  def initialize() {
    mergeReferenceAndSave(appConfig().underlying)
  }

  protected def refFilename = "reference/%s".format(referenceConfigFilename)
  protected val alreadyRun = new AtomicBoolean(false)

  // invoked using reflection.
  def once(): Boolean = {
    Registry.add(namespace, this)
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
      case e: Throwable =>
        val msg = "Reference configuration %s not found or invalid: %s".format(
          refFilename, e.getMessage
        )
        InternalTattler.system(msg)
        logger.error(msg, e)
        referenceConfig = None
    }
    true
  }

  final protected[config] def onChange(newConfig: TypesafeConfiguration) {
    try {
      mergeReferenceAndSave(newConfig)
      afterChange()
    } catch {
      case e: Throwable =>
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
        case e: Throwable =>
          val msg = "Error validating configuration for %s: %s".format(
            getClass.getName, e.getMessage
          )
          InternalTattler.system(msg)
          logger.error(msg, e)
          self.underlying = savedConfig
          throw e
      }
    } catch {
      case e: Throwable =>
        val msg = "Exception in mergeReferenceAndSave: %s".format(e.getMessage)
        InternalTattler.system(msg)
        logger.error(msg, e)
        throw e
    }
  }
}
