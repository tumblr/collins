package util

import play.api.Play
import java.text.SimpleDateFormat
import java.util.Date

object Helpers {

  val ISO_8601_FORMAT = "yyyy-MM-dd'T'HH:mm:ssz"
  private[this] val DATE_FORMAT = new SimpleDateFormat(ISO_8601_FORMAT)
  def dateFormat(date: Date): String = {
    val result = DATE_FORMAT.format(date)
    result.substring(0, 19) + result.substring(22, result.length)
  }

  def formatPowerPort(label: String) = {
    import models.AssetMeta.Enum.PowerPort
    PowerPort.toString + "_" + label
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
