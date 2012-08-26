package util
package config

import com.typesafe.config.{Config => TypesafeConfig}
import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import scala.collection.JavaConverters._
import java.io.File

/**
 * TODO
 *  - Hook into file watcher
 *  - Thread safety
 *  - Add basic message helper for message related code
 *  - Deprecate old Config class
 *  - Deprecate old Feature class
 *  - What about configs that aren't in separate files? Really you want to know when a namespace
 *  changes not neccesarily a particular file
 */

trait Configurable extends DelayedInit {
  val namespace: String
  val referenceConfigFilename: String

  implicit val configValue: ConfigurationRequirement = ConfigValue.Optional

  protected def validateConfig()

  protected def getObjectMap(key: String): Map[String,ConfigObject] =
    getValue(key, _.getObject(key).toConfig.root.asScala.map {
      case(key: String, value: ConfigObject) => (key -> value)
    }).getOrElse(Map.empty[String,ConfigObject]).toMap
  protected def getIntList(key: String): List[Int] =
    getValue(key, _.getIntList(key).asScala.toList.map(_.toInt)).getOrElse(List())
  protected def getString(key: String)(implicit cfgv: ConfigurationRequirement): Option[String] =
    getValue(key, _.getString(key)) match {
      case None if cfgv == ConfigValue.Required =>
        throw new Exception("Required configuration %s not found".format(key))
      case o => o
    }
  protected def getStringList(key: String): List[String] =
    getValue(key, _.getStringList(key).asScala.toList).getOrElse(List())
  protected def getStringSet(key: String): Set[String] = getStringList(key).toSet
  protected def getBoolean(key: String): Option[Boolean] = getValue(key, _.getBoolean(key))
  protected def getBoolean(key: String, default: Boolean): Boolean = getBoolean(key).getOrElse(default)

  private var referenceConfig: Option[TypesafeConfig] = None
  protected var underling: Option[TypesafeConfig] = None
 
  def onChange(file: File) {
    try {
      val savedConfig = underling
      val _cfg = ConfigFactory.parseFileAnySyntax(file)
      val mergedConfig = referenceConfig.map { rc =>
        val merged = _cfg.withFallback(rc)
        merged.checkValid(rc, namespace)
        merged.getConfig(namespace)
      }.getOrElse(_cfg)
      underling = Some(mergedConfig)
      try {
        validateConfig()
      } catch {
        case e =>
          underling = savedConfig
      }
    } catch {
      case e =>
        println(e)
    }
  }

  private def getValue[V](path: String, p: TypesafeConfig => V): Option[V] = try {
    underling match {
      case None => None
      case Some(c) => Option(p(c))
    }
  } catch {
    case e: ConfigException.Missing =>
      None
  }

  def delayedInit(x: => Unit) {
    x
    referenceConfig = try {
      var fname = Option(getClass.getClassLoader.getResource(referenceConfigFilename))
                  .map(_.getFile)
                  .getOrElse(referenceConfigFilename)
      val file = new File(fname)
      Some(ConfigFactory.parseFileAnySyntax(file))
    } catch {
      case e =>
        referenceConfig = None
        throw e
    }
    Registry.register(namespace, this)
  }
}
