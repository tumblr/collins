package util
package config

import com.typesafe.config.{ConfigException, ConfigFactory, ConfigObject}
import play.api.Configuration
import java.io.File
import java.util.concurrent.atomic.AtomicReference

case class ConfigWatch private[config](config: Configuration) extends FileWatcher {
  override def millisBetweenFileChecks = 15000L
  override def filename = Option(config.underlying.origin.filename).orElse {
    val cfg = config.underlying
    val refValue = cfg.getValue("application.secret")
    val origin = refValue.origin
    Option(origin.filename)
  }.getOrElse {
    throw new Exception("Config has no file based origin, can not watch for changes")
  }
  override def onError(file: File) {
  }
  override def onChange(file: File) {
    val config = ConfigFactory.load(
      ConfigFactory.parseFileAnySyntax(file)
    ) // this is what Play does
    Registry.onChange(config)
  }
}

object ConfigWatch {
  private val i = new AtomicReference[Option[ConfigWatch]](None)
  def apply() = i.get().getOrElse {
    throw new Exception("ConfigWatch not yet initialized")
  }
  protected[config] def initialize(cfg: Configuration) {
    i.compareAndSet(None, Some(new ConfigWatch(cfg)))
  }
}
