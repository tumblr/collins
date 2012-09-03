package util
package config

import play.api.Application

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import scala.collection.JavaConverters._

object Registry {

  private val registered = new ConcurrentHashMap[String,Configurable]()
  private val initialized = new AtomicBoolean(false)
  private val logger = play.api.Logger(getClass)

  def add(name: String, config: Configurable) {
    logger.info("Registered %s".format(name))
    registered.put(name, config)
  }

  def onChange(config: TypesafeConfiguration) {
    registered.values.asScala.foreach { c => c.onChange(config) }
  }

  def shutdown() {
    ConfigWatch.stop()
  }

  /**
   * Here we discover all classes implementing Configurable and call the apply() method which brings
   * that singleton instance to life, and in the process handles basic initialization and ensures
   * registration. Thus when validate is called, all classes extending Configurable are validated.
   */
  def initializeAll(app: Application) {
    if (initialized.compareAndSet(false, true)) {
      ConfigWatch.start
      logger.info("Initializing all subclasses")
      getSubclassesOfConfigurable(app).foreach { k =>
        val klassName = getNormalizedClassName(k.getName)
        logger.info("Initializing %s".format(klassName))
        getClassFromName(klassName).foreach { klass =>
          callOnceOnClass(klass)
        }
      }
    } else {
      logger.warn("Already initialized")
    }
  }

  def validate() {
    registered.values.asScala.foreach { c => c.initialize() }
  }

  // Given a class attempt to call the apply method on it
  protected def callOnceOnClass[_](klass: Class[_]) = try {
    val meth = klass.getDeclaredMethod("once")
    meth.invoke(null)
  } catch {
    case e =>
      logger.info("Error calling once method on %s: %s".format(klass.getName, e.getMessage), e)
  }

  // Given a class name return the class
  protected def getClassFromName(name: String) = try {
    Some(Class.forName(name))
  } catch {
    case e =>
      logger.info("Class name %s is invalid".format(name))
      None
  }

  // Convert a possible companion object name to a reflection friendly name
  protected def getNormalizedClassName(name: String): String = name.replace("$", "")

  // Return a Set of classes extending util.config.Configurable
  protected def getSubclassesOfConfigurable(app: Application) = {
    app.configuration.underlying.getStringList("config.validations").asScala.toSet[String].map { k =>
      Class.forName(k)
    }
  }
}
