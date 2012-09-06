package collins
package script

import util.config.Configurable

case class CollinScriptConfigException(source: String)
  extends Exception("Didn't find %s in configuration for collinscript".format(source))


object CollinScriptConfig extends Configurable {

  override val namespace = "collinscript"
  override val referenceConfigFilename = "collinscript_reference.conf"

  def enabled = getBoolean("enabled", false)
  def refreshPeriod = getLong("refreshPeriod", 5000)
  def scriptDir = getString("scriptDir").getOrElse{
    throw CollinScriptConfigException("scriptDir")
  }

  override protected def validateConfig() {
    if (!enabled) {
      return
    }
    scriptDir
  }

}
