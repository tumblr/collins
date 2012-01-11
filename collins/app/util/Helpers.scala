package util

import play.api.Play
import play.api.Mode
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

  def mapToQueryString(prefix: String, map: Map[String, Seq[String]]): String = {
    val qs = map.map { case(k,v) =>
      v.map{s => "%s=%s".format(k, java.net.URLEncoder.encode(s,"UTF-8"))}.mkString("&")
    }.mkString("&")
    prefix match {
      case hasQs if hasQs.contains("?") => hasQs + "&" + qs
      case noQs => noQs + "?" + qs
    }
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
