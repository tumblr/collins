package util
package config

import play.api.PlayException
import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject, ConfigValue => TypesafeConfigValue}
import scala.collection.JavaConverters._
import java.net.{MalformedURLException, URL}
import java.util.concurrent.atomic.AtomicReference

// Provide access to values from an underlying configuration
trait ConfigAccessor {

  private val _underlying: AtomicReference[Option[TypesafeConfiguration]] = new AtomicReference(None)

  def ns: Option[String] = None

  def globalError(message: String, e: Option[Throwable] = None) =
    new PlayException("Confguration error", message, e)

  protected def underlying = {
    _underlying.get()
  }

  protected def underlying_=(config: Option[TypesafeConfiguration]) {
    _underlying.set(config)
  }

  protected def getBoolean(key: String): Option[Boolean] = getValue(key, _.getBoolean(key))
  protected def getBoolean(key: String, default: Boolean): Boolean =
    getBoolean(key).getOrElse(default)

  protected def getConfig(key: String): TypesafeConfiguration =
    getValue(key, _.getObject(key)).map(_.toConfig).getOrElse(ConfigFactory.empty).resolve

  protected def getConfigValue(key: String): Option[TypesafeConfigValue] =
    getValue(key, _.getValue(key))

  protected def getInt(key: String): Option[Int] = getValue(key, _.getInt(key))
  protected def getInt(key: String, default: Int): Int =
    getInt(key).getOrElse(default)

  protected def getIntList(key: String): List[Int] =
    getValue(key, _.getIntList(key).asScala.toList.map(_.toInt)).getOrElse(List())

  protected def getLong(key: String): Option[Long] = getValue(key, _.getLong(key))
  protected def getLong(key: String, default: Long): Long =
    getLong(key).getOrElse(default)

  protected def getMilliseconds(key: String): Option[Long] = getValue(key, _.getMilliseconds(key))

  protected def getObjectMap(key: String): Map[String,ConfigObject] =
    getValue(key, _.getObject(key).toConfig.root.asScala.map {
      case(key: String, value: ConfigObject) => (key -> value)
    }).getOrElse(Map.empty[String,ConfigObject]).toMap

  protected def getStringMap(key: String): Map[String,String] =
    getValue(key, _.getObject(key).toConfig.root.asScala.map { case(key: String, value) =>
      (key -> value.unwrapped.toString)
    }).getOrElse(Map.empty[String,String]).toMap

  protected def getString(key: String, default: String): String =
    getString(key)(ConfigValue.Optional).getOrElse(default)

  protected def getString(key: String, validValues: Set[String]): Option[String] =
    getString(key)(ConfigValue.Optional) match {
      case None => None
      case Some(v) => validValues.contains(v) match {
        case true => Some(v)
        case false =>
          throw new Exception("Value %s for key %s is not one of %s".format(
            v, key, validValues.mkString(", ")
          ))
      }
    }

  protected def getString(key: String)(implicit cfgv: ConfigRequirement): Option[String] =
    getValue(key, _.getString(key)) match {
      case None =>
        cfgv match {
          case ConfigValue.Required =>
            val keyname = getFqNs(key)
            throw new Exception("Required configuration %s not found".format(keyname))
          case _ =>
            None
        }
      case o =>
        o
    }

  protected def getStringList(key: String): List[String] =
    getValue(key, _.getStringList(key).asScala.toList).getOrElse(List())

  protected def getStringSet(key: String): Set[String] = getStringList(key).toSet
  protected def getStringSet(key: String, default: Set[String]): Set[String] = {
    val set = getStringSet(key)
    if (set.isEmpty)
      default
    else
      set
  }

  protected def getUrl(key: String): Option[URL] = {
    getString(key)(ConfigValue.Optional)
      .filter(_.nonEmpty)
      .flatMap { url =>
        try {
          Some(new URL(url))
        } catch {
          case e: MalformedURLException =>
            None
        }
      }
  }

  // get a fully qualified namespace + key if namespace is specified
  protected def getFqNs(key: String) = ns.map(n => "%s.%s".format(n, key)).getOrElse(key)

  private def getValue[V](path: String, p: TypesafeConfiguration => V): Option[V] = try {
    underlying.flatMap(cfg => Option(p(cfg)))
  } catch {
    case e: ConfigException.Missing =>
      None
  }
}
