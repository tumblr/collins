package util
package config

import play.api.Logger
import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import java.io.File
import scala.collection.JavaConverters._
import java.util.{Timer, TimerTask}
import java.util.concurrent.ConcurrentHashMap

class ConfigWatchTask(watchedFiles: Map[File,Long]) extends TimerTask {
  private val logger = Logger("ConfigWatchTask")
  private val map = new ConcurrentHashMap[File,Long](watchedFiles.asJava)
  override def run() {
    logger.debug("Reviewing map file file changes")
    val changeCount = map.entrySet.asScala.foldLeft(0) { case (count, mapEntry) =>
      val file = mapEntry.getKey.asInstanceOf[File]
      val modTime = mapEntry.getValue.asInstanceOf[Long]
      if (file.lastModified > modTime) {
        logger.info("File %s changed modTime from %s to %s".format(
          file.toString, modTime.toString, file.lastModified
        ))
        map.replace(file, file.lastModified)
        count + 1
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

object ConfigWatch extends AppConfig {

  private val logger = Logger("ConfigWatch")

  private val rootConfig: File = Option(appConfig().underlying.origin.filename).orElse {
    Option(System.getProperty("config.file"))
  }.orElse {
    val cfg = appConfig().underlying
    try {
      val refValue = cfg.getValue("application.secret")
      val origin = refValue.origin
      Option(origin.filename)
    } catch {
      case e => None
    }
  }.map(f => new File(f)).getOrElse {
    throw new Exception("Config has no file based origin, can not watch for changes")
  }

  private val allWatches: Map[File,Long] = {
    val cfg = appConfig().underlying
    val files = cfg.root.unwrapped.asScala.keys.map { key =>
      Option(cfg.root.get(key).origin.filename)
    }.filter(_.isDefined).map(s => new File(s.get)).toSet + rootConfig
    files.map { file =>
      file -> 0L
    }.toMap
  }

  private val timer = new Timer()

  Runtime.getRuntime().addShutdownHook(new Thread() {
    override def run() {
      try {
        timer.cancel
      } catch {
        case e =>
      }
    }
  })

  def start() {
    logger.info("Scheduling ConfigWatch timer")
    try {
      // start a timer task in 30 seconds that checks the file times every 30 seconds
      timer.schedule(new ConfigWatchTask(allWatches), 30000L, 30000L)
    } catch {
      case e =>
        logger.warn("Error scheduling timer: %s".format(e.getMessage))
    }
  }

  def stop() {
    try {
      logger.info("Cancelling ConfigWatch timer")
      timer.cancel
    } catch {
      case e =>
    }
  }

  protected[config] def onChange() {
    try {
      val config = ConfigFactory.load(
        ConfigFactory.parseFileAnySyntax(rootConfig)
      ) // this is what Play does
      Registry.onChange(config)
    } catch {
      case e =>
        logger.warn("Error loading configuration from %s: %s".format(
          rootConfig.toString, e.getMessage
        ), e)
    }
  }
}
