package collins.events

import java.io.File

import collins.util.MessageHelper
import collins.util.config.Configurable

object EventsConfig extends Configurable {

  override val namespace = "events"
  override val referenceConfigFilename = "events_reference.conf"

  def enabled = getBoolean("enabled", false)

  override protected def validateConfig() {
  }
}