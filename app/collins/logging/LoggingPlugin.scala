package collins
package logging

import play.api.{Application, Logger, Play, Plugin}
import java.io.File

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
    if (Play.isDev(app)) {
      Option(this.getClass.getClassLoader.getResource("dev_logger.xml"))
        .map(_.getFile())
        .foreach { file =>
          System.setProperty("logger.file", file)
          Logger.init(new File("."))
        }
    }
  }
}
