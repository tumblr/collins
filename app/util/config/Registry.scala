package util
package config

import com.typesafe.config.{Config => TypesafeConfig}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import play.api.Application
import scala.collection.JavaConverters._

/**
 * Registerable classes are Configurable as well that should be discovered upon system startup. This
 * discovery allows boot time validation of all Configurable classes.
 */
abstract class Registerable[T <: Configurable](c: => T) {
  private val Instance = c
  def apply() = Instance
}

object Registry {

  private val registered = new ConcurrentHashMap[String,Configurable]()
  private val initialized = new AtomicBoolean(false)
  private val logger = play.api.Logger(getClass)

  def add(name: String, config: Configurable) {
    logger.info("Registered %s".format(name))
    registered.put(name, config)
  }

  def onChange(config: TypesafeConfig) {
    registered.values.asScala.foreach { c => c.onChange(config) }
  }

  /**
   * Here we discover all classes implementing Registerable and call the apply() method which brings
   * that singleton instance to life, and in the process handles basic initialization and ensures
   * registration. Thus when validate is called, all classes extending Registerable are validated.
   */
  def initializeAll(app: Application) {
    if (initialized.compareAndSet(false, true)) {
      ConfigWatch.initialize(app.configuration)
      logger.info("Initializing all subclasses")
      getSubclassesOfRegisterable(app).foreach { k =>
        val klassName = getNormalizedClassName(k.getName)
        logger.info("Initializing %s".format(klassName))
        getClassFromName(klassName).foreach { klass =>
          callApplyOnClass(klass)
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
  protected def callApplyOnClass[_](klass: Class[_]) = try {
    val meth = klass.getDeclaredMethod("apply")
    meth.invoke(null)
  } catch {
    case e =>
      logger.info("Error calling apply method on %s: %s".format(klass.getName, e.getMessage), e)
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

  // Return a Set of classes extending util.config.Registerable
  protected def getSubclassesOfRegisterable(app: Application) = {
    import org.reflections._
    import org.reflections.util.{ConfigurationBuilder, FilterBuilder, ClasspathHelper}
    import org.reflections.scanners._
    try {
      val r = new Reflections(new ConfigurationBuilder()
        .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix("")))
        .setUrls(ClasspathHelper.forPackage(".", app.classloader))
        .setScanners(new SubTypesScanner()))
      val c = Class.forName("util.config.Registerable")
      r.getSubTypesOf(c).asScala
    } catch {
      case e =>
        logger.error("Error finding registerable: %s".format(e.getMessage), e)
        Set()
    }
  }
}
