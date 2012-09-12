package collins
package script

import play.api.{Application, Plugin}

class CollinScriptPlugin(app: Application) extends Plugin {

  override def enabled = CollinScriptConfig.enabled

  override def onStart() {
    if (enabled) {
      CollinScriptRegistry.initializeAll(app)
    }
  }

  override def onStop() {
    if (enabled) {
      CollinScriptRegistry.shutdown
    }
  }

}
