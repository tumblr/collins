package util.plugins

import collins.graphs.{GraphPlugin => GraphPlayPlugin}
import play.api.{Application, Mode, Play}

object GraphPlugin {
  def option(): Option[GraphPlayPlugin] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[GraphPlayPlugin].filter(_.enabled)
    }
  }
}
