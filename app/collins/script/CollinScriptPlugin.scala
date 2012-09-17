package collins
package script

import models.Asset
import play.api.{Application, Plugin}


class CollinScriptPlugin(app: Application) extends Plugin {

  override def enabled: Boolean = {
    CollinScriptConfig.pluginInitialize(app.configuration)
    CollinScriptConfig.enabled
  }

  override def onStart() {
    if (enabled) {
      Asset.findById(1).get
      CollinScriptRegistry.initializeAll(app)
    }
  }

  override def onStop() {
    if (enabled) {
      CollinScriptRegistry.shutdown
    }
  }

}
