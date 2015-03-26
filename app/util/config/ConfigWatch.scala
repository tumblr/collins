package util
package config

import play.api.Logger
import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import scala.collection.JavaConverters._
import java.io.File
import java.util.Timer

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
      case e: Throwable => None
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

  private val timer = new Timer("config-watcher")

  Runtime.getRuntime().addShutdownHook(new Thread("config-watch-reaper") {
    override def run() {
      try {
        timer.cancel
      } catch {
        case e: Throwable =>
      }
    }
  })

  def start() {
    logger.info("Scheduling ConfigWatch timer")
    val startAfter = if (AppConfig.isDev) 10000L else 30000L
    val runEvery = if (AppConfig.isDev) 10000L else 30000L
    try {
      // start a timer task in 30 seconds that checks the file times every 30 seconds
      timer.schedule(new ConfigWatchTask(allWatches), startAfter, runEvery)
    } catch {
      case e: Throwable =>
        logger.warn("Error scheduling timer: %s".format(e.getMessage))
    }
  }

  def stop() {
    try {
      logger.info("Cancelling ConfigWatch timer")
      timer.cancel
    } catch {
      case e: Throwable =>
    }
  }

  protected[config] def onChange() {
    try {
      val config = ConfigFactory.load(
        ConfigFactory.parseFileAnySyntax(rootConfig)
      ) // this is what Play does
      Registry.onChange(config)
    } catch {
      case e: Throwable =>
        logger.warn("Error loading configuration from %s: %s".format(
          rootConfig.toString, e.getMessage
        ), e)
    }
  }
}
