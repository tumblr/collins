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
  def clean: ListConfig = toUpperCase.trim.nonEmpty
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

  def getString(name: String, key: String, default: String): StringConfig = {
    StringConfig(get(name).flatMap(_.getString(key)).getOrElse(default))
  }

  def getBoolean(name: String, key: String): Option[Boolean] = {
    get(name).flatMap(_.getBoolean(key))
  }
}

object Config extends Config {
  def statusAsSet(name: String, key: String, default: String = ""): Set[Int] = {
    getString(name, key, default).split(",").clean.map { name =>
      Status.findByName(name).map(_.id).getOrElse(-1)
    }.toSet[Int]
  }
  def tagsAsSet(name: String, key: String, default: String = ""): Set[String] = {
    getString(name, key, default).split(",").clean.toSet
  }
}
