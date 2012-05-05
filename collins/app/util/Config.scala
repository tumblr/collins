package util

import models.Status
import play.api.{Configuration, Play}

trait ConfigType[T <: AnyRef] {
  def toUpperCase: ConfigType[T]
  def trim: ConfigType[T]
}

case class ListConfig(source: List[String]) extends ConfigType[List[String]] {
  def toUpperCase: ListConfig = ListConfig(source.map(_.toUpperCase))
  def trim: ListConfig = ListConfig(source.map(_.trim))
  def clean: ListConfig = clean()
  def clean(upcase: Boolean = true): ListConfig = {
    Option(upcase).filter(_ == true).map(_ => toUpperCase)
      .getOrElse(this)
      .trim
      .nonEmpty
  }
  def filter(f: String => Boolean): ListConfig = ListConfig(
    source.filter(f(_))
  )
  def nonEmpty: ListConfig = filter(_.nonEmpty)
  def map[T](f: String => T): List[T] = {
    source.map(f(_))
  }
  def toSet = source.toSet
}

case class StringConfig(source: String) extends ConfigType[String] {
  def split(token: String) = ListConfig(source.split(token).toList)
  def toUpperCase = StringConfig(source.toUpperCase)
  def trim = StringConfig(source.trim)
  override def toString: String = source
}

trait Config {
  def source: Map[String,String] = Map.empty
  def get(name: String): Option[Configuration] = source match {
    case usePlay if usePlay.isEmpty =>
      Play.maybeApplication.map { app =>
        app.configuration.getConfig(name)
      }.getOrElse(None)
    case useSource =>
      Configuration.from(useSource).getConfig(name)
  }
  def get(): Option[Configuration] = source match {
    case usePlay if usePlay.isEmpty =>
      Play.maybeApplication.map(_.configuration)
    case useSource =>
      Some(Configuration.from(useSource))
  }

  def getString(key: String, default: String): StringConfig = {
    StringConfig(get().flatMap(_.getString(key)).getOrElse(default))
  }
  def getString(name: String, key: String, default: String): StringConfig = {
    StringConfig(get(name).flatMap(_.getString(key)).getOrElse(default))
  }

  def getBoolean(key: String): Option[Boolean] = {
    get().flatMap(_.getBoolean(key))
  }
  def getBoolean(name: String, key: String): Option[Boolean] = {
    get(name).flatMap(_.getBoolean(key))
  }

  def getStringSet(name: String, key: String, default: Option[String] = None, upcase: Boolean = true): Set[String] = {
    val ds = default.getOrElse("")
    getString(name, key, ds).split(",").clean(upcase).toSet
  }

  def toMap(): Map[String,String] = {
    get().map(cfg => cfg.keys.filter(!_.contains("akka")).map(k => k -> cfg.getString(k).get).toMap)
      .getOrElse(Map.empty)
  }

  def toMap(name: String): Map[String,String] = {
    get(name).map(cfg => cfg.keys.map(k => k -> cfg.getString(k).get).toMap)
      .getOrElse(Map.empty)
  }
}

object Config extends Config {

  def apply(src: Map[String,String]) = {
    new Config {
      override def source = src
    }
  }

  def statusAsSet(name: String, key: String, default: String = ""): Set[Int] = {
    getString(name, key, default).split(",").clean.map { name =>
      Status.findByName(name).map(_.id).getOrElse(-1)
    }.toSet[Int]
  }

}
