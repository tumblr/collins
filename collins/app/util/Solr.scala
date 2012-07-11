import org.apache.solr.client.solrj._
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer
import org.apache.solr.core.CoreContainer

import play.api.{Application, Configuration, Logger, PlayException, Plugin}

class SolrPlugin(app: Application) extends Plugin {

  private[this] var _server: Option[SolrServer] = None

  def server = _server.get //FIXME: make the thrown exception more descriptive

  lazy val solrHome = app.configuration.getConfig("solr").flatMap{_.getString("solrHome")}.getOrElse(throw new Exception("No solrHome set!"))
  override lazy val enabled = app.configuration.getConfig("solr").flatMap{_.getBoolean("enabled")}.getOrElse(false)


  override def onStart() {
    if (enabled) {
      System.setProperty("solr.solr.home",solrHome);
      val initializer = new CoreContainer.Initializer();
      val coreContainer = initializer.initialize();
      _server = Some(new EmbeddedSolrServer(coreContainer, ""));
    }
  }


}

object Solr {

  def query(q: SolrQuery) = Nil

}
