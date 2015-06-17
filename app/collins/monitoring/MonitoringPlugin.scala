package collins.monitoring

import play.api.Application
import play.api.Plugin
import play.twirl.api.Content

import collins.models.asset.AssetView

class MonitoringPlugin(override val app: Application) extends MonitoringView with Plugin {

  protected var underlying: Option[MonitoringView] = None

  override def enabled = {
    MonitoringConfig.pluginInitialize(app.configuration)
    MonitoringConfig.enabled
  }
  override def onStart() {
    val instance = getMonitoringInstance()
    instance.validateConfig()
    underlying = Some(instance)
  }
  override def onStop() {
    underlying = None
  }

  override def getContent(asset: AssetView): Option[Content] = underlying.flatMap(_.getContent(asset))
  override def isMonitorable(asset: AssetView): Boolean = {
    if (enabled) {
      return underlying.get.isMonitorable(asset)
    }
    return false
  }


  protected def getMonitoringInstance(): MonitoringView = {
    this.getClass.getClassLoader.loadClass(MonitoringConfig.className)
      .getConstructor(classOf[Application])
      .newInstance(app)
      .asInstanceOf[MonitoringView]
  }

}
