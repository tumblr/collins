package util
package config

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import java.io.File
import scala.collection.JavaConverters._

object ConfigWatch extends FileWatcher with AppConfig {

  def config = appConfig()

  val DummyFile = System.getProperty("java.io.tmpdir")

  private lazy val rootConfig: String = Option(config.underlying.origin.filename).orElse {
    Option(System.getProperty("config.file"))
  }.orElse {
    val cfg = config.underlying
    try {
      val refValue = cfg.getValue("application.secret")
      val origin = refValue.origin
      Option(origin.filename)
    } catch {
      case e =>
        println("Error doing stuff in %s: %s".format(cfg.origin, e.getMessage))
        Option(DummyFile)
    }
  }.getOrElse {
    throw new Exception("Config has no file based origin, can not watch for changes")
  }

  private lazy val otherWatches: Map[String,FileWatcher] = {
    if (rootConfig == DummyFile) {
      Map.empty
    } else {
      val cfg = config.underlying
      val files = cfg.root.unwrapped.asScala.keys.map { key =>
        Option(cfg.root.get(key).origin.filename)
      }.filter(_.isDefined).map(_.get).toSet - rootConfig
      files.map { file =>
        logger.info("Setting up watch on %s".format(file))
        file -> FileWatcher.watch(file, 15, true) { f =>
          onChange(new File(rootConfig))
        }
      }.toMap
    }
  }

  override def delayInitialCheck = true
  override def millisBetweenFileChecks = 15000L
  override def filename = {
    // Do not change this ordering
    val rc = rootConfig
    otherWatches.foreach { case(key,value) => value.tick }
    rc
  }

  override def onError(file: File) {
  }
  override def onChange(file: File) {
    if (file.getAbsolutePath.startsWith(DummyFile)) {
      return
    }
    try {
      val config = ConfigFactory.load(
        ConfigFactory.parseFileAnySyntax(file)
      ) // this is what Play does
      Registry.onChange(config)
    } catch {
      case e =>
        logger.warn("Error loading configuration from %s: %s".format(
          file.toString, e.getMessage
        ), e)
    }
  }
}
