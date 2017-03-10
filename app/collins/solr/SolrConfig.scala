package collins.solr

import java.io.File

import scala.concurrent.duration.DurationInt

import collins.callbacks.CallbackConfig
import collins.util.MessageHelper
import collins.util.config.Configurable
import collins.util.config.ConfigurationException

object SolrConfig extends Configurable {
  override val namespace = "solr"
  override val referenceConfigFilename = "solr_reference.conf"

  object Messages extends MessageHelper("solr") {
    def invalidHome(t: String) =
      messageWithDefault("invalidHome", "solr.embeddedSolrHome %s is invalid".format(t), t)
    def invalidUrl(t: String) =
      messageWithDefault("invalidUrl", "solr.externalUrl %s is invalid".format(t), t)
    def callbacksNotEnabled =
      messageWithDefault("callbacksNotEnabled", "Solr support requires callback, ensure callbacks are enabled.")
  }

  // defaults taken from http://wiki.apache.org/solr/Solrj#Changing_other_Connection_Settings
  def embeddedSolrHome = getString("embeddedSolrHome", "NONE")
  def enabled = getBoolean("enabled", true)
  def externalUrl = getUrl("externalUrl")
  def reactToUpdates = getBoolean("reactToUpdates", true)
  def repopulateOnStartup = getBoolean("repopulateOnStartup", false)
  def useEmbeddedServer = getBoolean("useEmbeddedServer", true)
  def socketTimeout = getInt("socketTimeout", 1000)
  def connectionTimeout = getInt("connectionTimeout", 5000)
  def maxTotalConnections = getInt("maxTotalConnections", 100)
  def commitWithin = getInt("commitWithinMs", 200)
  def defaultMaxConnectionsPerHost = getInt("defaultMaxConnectionsPerHost", 100)
  def assetBatchUpdateWindow = getInt("assetBatchUpdateWindowMs", 100) milliseconds

  override protected def validateConfig() {
    if (enabled) {
      require(CallbackConfig.enabled, Messages.callbacksNotEnabled)
      if (useEmbeddedServer) {
        val path = new File(embeddedSolrHome)
        require(path.isDirectory && path.canWrite, Messages.invalidHome(path.toString))
      } else {
        require(externalUrl.isDefined, Messages.invalidUrl(getString("externalUrl", "")))
      }
    }
  }
}
