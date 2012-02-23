package util

import play.api.{Play, Plugin}
import com.tumblr.play.state.ManagerPlugin

object StateManager {
  def pluginEnabled: Option[ManagerPlugin] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[ManagerPlugin].filter(_.enabled)
    }
  }

  def pluginEnabled[T](fn: ManagerPlugin => T): Option[T] = {
    pluginEnabled.map { p =>
      fn(p)
    }
  }
}
