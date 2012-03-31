package util

import play.api.{Mode, Play}

object Helpers {

  def isProd() = getApplicationMode() == Mode.Prod
  def getApplicationMode(): Mode.Mode = {
    Play.maybeApplication.map { app =>
      app.mode
    }.getOrElse(Mode.Dev)
  }
}
