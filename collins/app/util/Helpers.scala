package util

import play.api.{Configuration, Mode, Play}
import models.Asset
import java.text.SimpleDateFormat
import java.util.Date

object Helpers {

  val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
  private[this] val DATE_FORMAT = new SimpleDateFormat(ISO_8601_FORMAT)
  def dateFormat(date: Date): String = {
    DATE_FORMAT.format(date)
  }

  def formatDouble(d: Double) = "%.2f".format(100*d)

  private[this] val words = """([a-zA-Z]+)""".r
  private[this] val separators = """([^a-zA-Z]+)""".r
  def camelCase(value: String, sep: String = "") = {
    separators.replaceAllIn(words.replaceAllIn(value, m => m.matched.toLowerCase.capitalize), sep)
  }

  def formatPowerPort(label: String) = {
    import models.AssetMeta.Enum.PowerPort
    PowerPort.toString + "_" + label
  }

  private[this] lazy val IgnoreAssets: Set[String] = getFeature("ignoreDangerousCommands")
      .map(_.split(",").toSet[String].map(_.toLowerCase))
      .getOrElse(Set[String]())
  def ignoreDangerousCommand(tag: String): Boolean = IgnoreAssets.contains(tag.toLowerCase)
  def ignoreDangerousCommand(asset: Asset): Boolean = {
    ignoreDangerousCommand(asset.tag)
  }

  def getConfig(name: String): Option[Configuration] = {
    Play.maybeApplication.map { app =>
      app.configuration.getConfig(name)
    }.getOrElse(None)
  }

  def haveFeature(name: String, flatten: Boolean = true): Option[Boolean] = {
    if (flatten) {
      getConfig("features").flatMap { _.getBoolean(name).filter{_ == true} }
    } else {
      getConfig("features").flatMap { _.getBoolean(name) }
    }
  }
  def getFeature(name: String): Option[String] = {
    getConfig("features").flatMap { _.getString(name) }
  }

  def subAsMap(subKey: String, default: Map[String,String] = Map.empty): Map[String,String] = {
    Play.maybeApplication.map { app =>
      if (subKey.isEmpty) {
        app.configuration.keys.map { key =>
          key -> app.configuration.getString(key).get
        }.toMap
      } else {
        app.configuration.getConfig(subKey).map { config =>
          config.keys.map { key =>
            key -> config.getString(key).get
          }.toMap
        }
      }.getOrElse(default)
    }.getOrElse(default)
  }

  def getApplicationMode(): Mode.Mode = {
    Play.maybeApplication.map { app =>
      app.mode
    }.getOrElse(Mode.Dev)
  }
}
