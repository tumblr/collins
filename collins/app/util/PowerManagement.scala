package util

import play.api.{Play, Plugin}
import com.tumblr.play.{PowerManagement => PowerMgmt}

object PowerManagement {
  def pluginEnabled: Option[PowerMgmt] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[PowerMgmt].filter(_.enabled)
    }
  }

  def pluginEnabled[T](fn: PowerMgmt => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }
}
