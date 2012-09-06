package collins
package script

import play.api.{Application, Plugin}

class CollinScriptPlugin(app: Application) extends Plugin {

  override def enabled = true

  override def onStart() {
    if (enabled) {
      CollinScriptRegistry.initializeAll(app)
    }
  }

}
