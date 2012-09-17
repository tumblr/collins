package collins
package script

import collins.validation.File
import util.config.{Configurable, ConfigValue}

import java.io.File


case class CollinScriptConfigException(source: String)
  extends Exception("Didn't find %s in configuration for collinscript".format(source))


object CollinScriptConfig extends Configurable {

  override val namespace = "collinscript"
  override val referenceConfigFilename = "collinscript_reference.conf"

  def enabled = getBoolean("enabled", false)
  def refreshPeriodMillis: Long = getMilliseconds("refreshPeriodMillis")
    .getOrElse(5000)
  def outputDir = getString("outputDir").getOrElse("%s/%s".format(
      System.getProperty("java.io.tmpdir"), "collinscript-classes"))
  def scriptDir = getString("scriptDir")(ConfigValue.Required).filter(_.nonEmpty).get

  override protected def validateConfig() {
    if (!enabled) {
      return
    }
    scriptDir
    val outputDirFile = new File(outputDir)
    outputDirFile.mkdirs
    if (!outputDirFile.canWrite) {
      throw new CollinScriptConfigException("outputDir is not writable")
    }
  }

}
