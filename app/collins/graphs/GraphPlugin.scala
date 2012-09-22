package collins.graphs

import models.asset.AssetView
import util.config.TypesafeConfiguration

import play.api.{Application, Plugin}
import play.api.mvc.Content

class GraphPlugin(app: Application) extends Plugin with GraphView {

  protected var underlying: Option[GraphView] = None
  // We don't use this
  override protected val source: TypesafeConfiguration = null

  override def enabled = {
    GraphConfig.pluginInitialize(app.configuration)
    GraphConfig.enabled
  }
  override def onStart() {
    val instance = getGraphInstance()
    instance.validateConfig()
    underlying = Some(instance)
  }
  override def onStop() {
    underlying = None
  }

  override def get(asset: AssetView): Option[Content] = {
    underlying.flatMap(_.get(asset))
  }

  protected def getGraphInstance(): GraphView = {
    this.getClass.getClassLoader.loadClass(GraphConfig.className)
      .getConstructor(classOf[TypesafeConfiguration])
      .newInstance(GraphConfig.getConfigForClass())
      .asInstanceOf[GraphView]
  }

}
