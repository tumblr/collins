package collins.util.config

import scala.collection.mutable.Buffer
import scala.collection.mutable.ArrayBuffer

import scala.collection.JavaConverters.asScalaBufferConverter

import play.api.Application

/**
 * See note on Configurable. Registry of Collins configurable instances.
 */
object Registry {

  /* registered is populated via a call back from the Configurable */
  private val registered = ArrayBuffer[Configurable]()
  private val logger = play.api.Logger(getClass)

  def add(name: String, config: Configurable) {
    logger.info("Registered %s".format(name))
    registered += config
  }

  def onChange(config: TypesafeConfiguration) {
    logger.info("Reloading configuration on change")
    registered.foreach { _.onChange(config) }
  }

  def terminateRegistry() {
    ConfigWatch.stop()
    registered.clear()
  }

  /**
   * Here we discover all classes implementing Configurable and call the apply() method which brings
   * that singleton instance to life, and in the process handles basic initialization and ensures
   * registration. Thus when validate is called, all classes extending Configurable are validated.
   */
  def setupRegistry(app: Application) {
    ConfigWatch.start
    logger.info("Initializing all subclasses")
    getSubclassesOfConfigurable(app).foreach { classes =>
      classes.foreach { k =>
        val klassName = getNormalizedClassName(k.getName)
        logger.info("Initializing %s".format(klassName))
        getClassFromName(klassName).foreach { klass =>
          callOnceOnClass(klass)
        }
      }
    }
    validate()
  }

  def validate() {
    registered.foreach { c => 
      logger.info("Initializing configuration of type" + c.getClass)
      c.initialize()
    }
  }

  // Given a class attempt to call the apply method on it
  protected def callOnceOnClass[_](klass: Class[_]) = try {
    val meth = klass.getDeclaredMethod("once")
    meth.invoke(null)
  } catch {
    case e: Throwable =>
      logger.info("Error calling once method on %s: %s".format(klass.getName, e.getMessage), e)
  }

  // Given a class name return the class
  protected def getClassFromName(name: String): Option[Class[_ <: Configurable]] = try {
    Some(Class.forName(name).asInstanceOf[Class[Configurable]])
  } catch {
    case e: Throwable =>
      logger.info("Class name %s is invalid".format(name))
      None
  }

  // Convert a possible companion object name to a reflection friendly name
  protected def getNormalizedClassName(name: String): String = name.replace("$", "")

  // Return a Set of classes extending util.config.Configurable
  protected def getSubclassesOfConfigurable(app: Application): Option[Buffer[Class[_]]] = {
    app.configuration.getStringList("config.validations").map { l => 
      l.asScala.map { Class.forName } 
    }
  }
}
