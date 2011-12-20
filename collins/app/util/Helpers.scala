package util

import play.api.Play
import java.text.SimpleDateFormat
import java.util.Date

object Helpers {

  val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
  private[this] val DATE_FORMAT = new SimpleDateFormat(ISO_8601_FORMAT)
  def dateFormat(date: Date): String = {
    DATE_FORMAT.format(date)
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
      app.configuration.getSub(subKey).map { config =>
        config.data.map { case(key, conf) =>
          key -> conf.value
        }
      }.getOrElse(default)
    }.getOrElse(default)
  }
}
