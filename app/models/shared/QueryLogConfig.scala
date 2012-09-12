package models
package shared

import util.config.Configurable

object QueryLogConfig extends Configurable {
  override val namespace = "querylog"
  override val referenceConfigFilename = "querylog_reference.conf"

  def enabled = getBoolean("enabled", false)
  def includeResults = getBoolean("includeResults", false)
  def prefix = getString("prefix", "")
  def frontendLogging = getBoolean("frontendLogging", false)

  override protected def validateConfig() {
    if (enabled) {
      includeResults
      prefix
    }
  }
}
