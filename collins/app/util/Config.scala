package util

import models.Status
import play.api.{Configuration, Play}

class ConfigFormatException(
  rootKey: String,
  name: String,
  message: String,
  vtype: String
) extends IllegalArgumentException {
  def path: String = (rootKey, name) match {
    case (p, n) if !p.isEmpty && n.isEmpty => p
    case (p, n) if p.isEmpty && !n.isEmpty => n
    case (p, n) if !p.isEmpty && !n.isEmpty => "%s.%s".format(p, n)
    case _ => "unknown"
  }
  override def getMessage(): String =
    "%s is of the wrong format and could not be converted to a %s value: %s".format(path, vtype, message)
}
case class CompoundFormatException(previous: ConfigFormatException, next: ConfigFormatException)
  extends ConfigFormatException("", "", "", "")
{
  protected val underlying: Seq[ConfigFormatException] = previous match {
    case cfe@CompoundFormatException(_, _) => cfe.underlying ++ Seq(next)
    case _ => Seq(previous, next)
  }
  override def getMessage(): String =
    "Multiple conversion errors caught: %s".format(underlying.map(_.getMessage).mkString(", "))
}
case class BooleanFormatException(rootKey: String, name: String, message: String)
  extends ConfigFormatException(rootKey, name, message, "boolean")
case class IntegerFormatException(rootKey: String, name: String, message: String)
  extends ConfigFormatException(rootKey, name, message, "integer")
case class LongFormatException(rootKey: String, name: String, message: String)
  extends ConfigFormatException(rootKey, name, message, "integer")
case class SetFormatException(rootKey: String, name: String, message: String)
  extends ConfigFormatException(rootKey, name, message, "set")
case class StringFormatException(rootKey: String, name: String, message: String)
  extends ConfigFormatException(rootKey, name, message, "string")

trait Config {
  def source: Map[String,String] = Map.empty

  def get(name: String): Option[Configuration] = get().flatMap(_.getConfig(name))
  def get(): Option[Configuration] = source match {
    case usePlay if usePlay.isEmpty =>
      Play.maybeApplication.map(_.configuration)
    case useSource =>
      Some(Configuration.from(useSource))
  }

  @throws(classOf[ConfigFormatException])
  def getString(name: String, default: String): String = expectString(name = name) {
    get().flatMap(_.getString(name)).getOrElse(default)
  }
  @throws(classOf[ConfigFormatException])
  def getString(rootKey: String, name: String, default: String): String = expectString(rootKey, name) {
    get(rootKey).flatMap(_.getString(name)).getOrElse(default)
  }

  @throws(classOf[ConfigFormatException])
  def getBoolean(name: String): Option[Boolean] = expectBool(name = name) {
    get().flatMap(_.getBoolean(name))
  }
  @throws(classOf[ConfigFormatException])
  def getBoolean(rootKey: String, name: String): Option[Boolean] = expectBool(rootKey, name) {
    get(rootKey).flatMap(_.getBoolean(name))
  }

  @throws(classOf[ConfigFormatException])
  def getMilliseconds(name: String): Option[Long] = expectLong(name = name) {
    get().flatMap(_.getMilliseconds(name))
  }
  @throws(classOf[ConfigFormatException])
  def getMilliseconds(rootKey: String, name: String): Option[Long] = expectLong(rootKey, name) {
    get(rootKey).flatMap(_.getMilliseconds(name))
  }

  @throws(classOf[ConfigFormatException])
  def getInt(name: String): Option[Int] = expectInt(name = name) {
    get().flatMap(_.getInt(name))
  }
  @throws(classOf[ConfigFormatException])
  def getInt(rootKey: String, name: String): Option[Int] = expectInt(rootKey, name) {
    get(rootKey).flatMap(_.getInt(name))
  }

  @throws(classOf[ConfigFormatException])
  def getStringSet(
    rootKey: String,
    name: String,
    default: Option[String] = None,
    upcase: Boolean = true
  ): Set[String] = expectSet(rootKey, name) {
    val ds = default.getOrElse("")
    val cleaned = cleanStrings(getString(rootKey, name, ds).split(","))
    upcaseStrings(cleaned, upcase).toSet
  }

  protected def cleanStrings(seq: Seq[String]): Seq[String] = seq.map(_.trim).filter(_.nonEmpty)
  protected def upcaseStrings(seq: Seq[String], upcase: Boolean) = upcase match {
    case true => seq.map(_.toUpperCase)
    case false => seq
  }

  def toMap(): Map[String,String] = {
    get().map(cfg => cfg.keys.filter(!_.contains("akka")).map(k => k -> cfg.getString(k).get).toMap)
      .getOrElse(Map.empty)
  }

  def toMap(name: String): Map[String,String] = {
    get(name).map(cfg => cfg.keys.map(k => k -> cfg.getString(k).get).toMap)
      .getOrElse(Map.empty)
  }

  protected def expectBool[A](rootKey: String = "", name: String = "")(f: => A): A =
    handleFormatException(rootKey, name, BooleanFormatException(rootKey, name, _), f)
  protected def expectInt[A](rootKey: String = "", name: String = "")(f: => A): A =
    handleFormatException(rootKey, name, IntegerFormatException(rootKey, name, _), f)
  protected def expectLong[A](rootKey: String = "", name: String = "")(f: => A): A =
    handleFormatException(rootKey, name, LongFormatException(rootKey, name, _), f)
  protected def expectSet[A](rootKey: String = "", name: String = "")(f: => A): A =
    handleFormatException(rootKey, name, SetFormatException(rootKey, name, _), f)
  protected def expectString[A](rootKey: String = "", name: String = "")(f: => A): A =
    handleFormatException(rootKey, name, StringFormatException(rootKey, name, _), f)
  protected def handleFormatException[A](
    rootKey: String, name: String, ex: String => ConfigFormatException, f: => A
  ): A = try {
    f
  } catch {
    case currentException: ConfigFormatException =>
      throw CompoundFormatException(currentException, ex("exception already thrown"))
    case e => throw ex(e.getMessage)
  }
}

case class ConfigImpl(src: Map[String,String]) extends Config {
  override def source = src
}

object Config extends Config {

  def apply(src: Map[String,String]): Config = ConfigImpl(src)

  def statusAsSet(rootKey: String, name: String, default: String = ""): Set[Int] = {
    cleanStrings(getString(rootKey, name, default).split(",")).map { name =>
      Status.findByName(name).map(_.id).getOrElse(-1)
    }.toSet[Int]
  }

}
