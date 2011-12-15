package util

import play.api.Play

object Helpers {
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
