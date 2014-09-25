package collins.solr

import util.config.Configurable
import util.MessageHelper
import java.io.File
import akka.util.duration._

object SolrConfig extends Configurable {
  override val namespace = "solr"
  override val referenceConfigFilename = "solr_reference.conf"

  object Messages extends MessageHelper("solr") {
    def invalidHome(t: String) =
      messageWithDefault("invalidHome", "solr.embeddedSolrHome %s is invalid".format(t), t)
    def invalidUrl(t: String) =
      messageWithDefault("invalidUrl", "solr.externalUrl %s is invalid".format(t), t)
  }

  def embeddedSolrHome = getString("embeddedSolrHome", "NONE")
  def enabled = true
  def externalUrl = getUrl("externalUrl")
  def reactToUpdates = getBoolean("reactToUpdates", true)
  def repopulateOnStartup = getBoolean("repopulateOnStartup", false)
  def useEmbeddedServer = getBoolean("useEmbeddedServer", true)
  def socketTimeout = getInt("socketTimeout",1000)
  def connectionTimeout = getInt("connectionTimeout",100)
  def maxTotalConnections = getInt("maxTotalConnections",100)
  def defaultMaxConnectionsPerHost = getInt("defaultMaxConnectionsPerHost",100)
  def assetBatchUpdateWindow = getInt("assetBatchUpdateWindowMs",10) milliseconds

  override protected def validateConfig() {
    if (!enabled) {
      return
    }
    if (useEmbeddedServer) {
      val path = new File(embeddedSolrHome)
      require(path.isDirectory && path.canWrite, Messages.invalidHome(path.toString))
    } else {
      require(externalUrl.isDefined, Messages.invalidUrl(getString("externalUrl", "")))
    }
  }
}
