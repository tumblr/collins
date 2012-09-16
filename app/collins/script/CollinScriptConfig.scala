package collins
package script

import collins.validation.File
import util.config.{Configurable, ConfigValue}


case class CollinScriptConfigException(source: String)
  extends Exception("Didn't find %s in configuration for collinscript".format(source))


object CollinScriptConfig extends Configurable {

  override val namespace = "collinscript"
  override val referenceConfigFilename = "collinscript_reference.conf"

  def enabled = getBoolean("enabled", false)
  def refreshPeriodMillis: Long = getMilliseconds("refreshPeriodMillis")
    .getOrElse(5000)
  def scriptDir = getString("scriptDir")(ConfigValue.Required).filter(_.nonEmpty).get

  override protected def validateConfig() {
    if (!enabled) {
      return
    }
    scriptDir
  }

}
