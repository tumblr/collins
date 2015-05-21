package collins.logging

import java.io.File

import play.api.Application
import play.api.Logger
import play.api.Play
import play.api.Plugin

class LoggingPlugin(app: Application) extends Plugin {

  override def enabled = true

  override def onStart() {
    if (enabled) {
      setupLogging
    }
  }

  override def onStop() {
  }

  protected def setupLogging() {
    if (Play.isDev(app) || Play.isTest(app)) {
      Option(this.getClass.getClassLoader.getResource("dev_logger.xml"))
        .map(_.getFile())
        .foreach { file =>
          System.setProperty("logger.file", file)
          Logger.init(new File("."))
        }
    }
  }
}
