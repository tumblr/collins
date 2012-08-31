package util
package solr

import config.Configurable

object SolrConfig extends Configurable {
  override val namespace = "solr"
  override val referenceConfigFilename = "solr_reference.conf"

  def embeddedSolrHome = getString("embeddedSolrHome", "NONE")
  def enabled = getBoolean("enabled", false)

  override protected def validateConfig() {
    if (enabled) {
      embeddedSolrHome
    }
  }
}
