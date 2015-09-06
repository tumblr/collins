package collins.util.config

import java.io.File
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap

import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.JavaConverters.mapAsJavaMapConverter

import play.api.Logger

class ConfigWatchTask(watchedFiles: Map[File, Long]) extends TimerTask {
  private val logger = Logger("ConfigWatchTask")
  private val map = new ConcurrentHashMap[File, Long](watchedFiles.asJava)
  override def run() {
    logger.debug("Reviewing map file file changes")
    val changeCount = map.entrySet.asScala.foldLeft(0) {
      case (count, mapEntry) =>
        val file = mapEntry.getKey.asInstanceOf[File]
        val modTime = mapEntry.getValue.asInstanceOf[Long]
        if (file.lastModified > modTime) {
          logger.info("File %s changed modTime from %s to %s".format(
            file.toString, modTime.toString, file.lastModified))
          map.replace(file, file.lastModified)
          if (modTime == 0L) {
            // don't trigger onchange for first run
            count
          } else {
            count + 1
          }
        } else {
          logger.trace("File %s has not changed".format(file.toString))
          count
        }
    }
    if (changeCount > 0) {
      ConfigWatch.onChange()
    }
  }
}
