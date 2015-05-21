package collins.util.plugins

import play.api.Play

import collins.graphs.{GraphPlugin => GraphPlayPlugin}

object GraphPlugin {
  def option(): Option[GraphPlayPlugin] = {
    Play.maybeApplication.flatMap { app =>
      app.plugin[GraphPlayPlugin].filter(_.enabled)
    }
  }
}
