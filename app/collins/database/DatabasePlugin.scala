package collins
package database

import play.api.{Application, Logger, Play, Plugin}
import models.Model

class DatabasePlugin(app: Application) extends Plugin {

  override def enabled = true

  override def onStart() {
    if (enabled) {
      Model.initialize()
    }
  }

  override def onStop() {
    if (enabled) {
      Model.shutdown()
    }
  }
}
