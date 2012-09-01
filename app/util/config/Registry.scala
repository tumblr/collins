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

  /**
   * Here we discover all classes implementing Configurable and call the apply() method which brings
   * that singleton instance to life, and in the process handles basic initialization and ensures
   * registration. Thus when validate is called, all classes extending Configurable are validated.
   */
  def initializeAll(app: Application) {
    if (initialized.compareAndSet(false, true) && !skipInitialization) {
      ConfigWatch.tick
      logger.info("Initializing all subclasses")
      getSubclassesOfConfigurable(app).foreach { k =>
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

  protected def skipInitialization: Boolean = {
    if (!AppConfig.isDev()) {
      // prod doesn't have the odd class reloader problem that dev does
      false
    } else {
      val file = new File("%s/registry.tmp".format(System.getProperty("java.io.tmpdir")))
      if (file.createNewFile) {
        logger.info("Created temp file, skipping initialization")
        file.deleteOnExit()
        false
      } else {
        logger.info("Tmp file %s already exists, no need to initialized".format(file.toString))
        true
      }
    }
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

  // Return a Set of classes extending util.config.Configurable
  protected def getSubclassesOfConfigurable(app: Application) = {
    import org.reflections._
    import org.reflections.util.{ConfigurationBuilder, FilterBuilder, ClasspathHelper}
    import org.reflections.scanners._
    try {
      val r = new Reflections(new ConfigurationBuilder()
        .filterInputsBy(new FilterBuilder().include(FilterBuilder.prefix("")))
        .addUrls(ClasspathHelper.forPackage(".", app.classloader))
        .addUrls(ClasspathHelper.forManifest())
        .setScanners(new SubTypesScanner()))
      val c = Class.forName("util.config.Configurable")
      r.getSubTypesOf(c).asScala
    } catch {
      case e =>
        logger.error("Error finding registerable: %s".format(e.getMessage), e)
        Set()
    }
  }
}
