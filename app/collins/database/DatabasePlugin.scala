package collins.database

import play.api.Application
import play.api.Plugin

import collins.models.shared.Model

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

  def closeConnection() {
    if (enabled) {
      Model.shutdown()
    }
  }
}
