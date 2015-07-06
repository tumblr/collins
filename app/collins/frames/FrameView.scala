package collins.frames

import scala.util.control.Exception

import play.api.Logger
import play.api.libs.json.Json

import collins.models.asset.AllAttributes

import javax.script.Invocable
import javax.script.ScriptEngineManager

case class ViewSpecs(val name: String, val title: String, style:String = "", enabled: Boolean = false, val url: String = "")

trait FrameView {
  def getViewSpecs(asset: AllAttributes): List[ViewSpecs]
}

object FrameView extends FrameView {

  private[this] val logger = Logger(getClass)

  override def getViewSpecs(asset: AllAttributes): List[ViewSpecs] = {
    val jsScriptEngine = new ScriptEngineManager(null).getEngineByName("JavaScript")
    ViewsConfig.frames.flatMap {
      fc => Exception.allCatch.opt({
        if (fc.enabled) {
          val assetJson = Json.stringify(asset.toJsValue())
          val activeScript = jsScriptEngine.eval(fc.script)
          val invocable = jsScriptEngine.asInstanceOf[Invocable]
          val enabled = invocable.invokeFunction("isEnabled", assetJson).asInstanceOf[Boolean]
          //val enabled = jsScriptEngine.eval("isEnabled(" + assetJson + ")").asInstanceOf[Boolean]
          if (enabled) {
            val url = invocable.invokeFunction("getUrl", assetJson).asInstanceOf[String]
            //val url = jsScriptEngine.eval("getUrl(" + assetJson + ")").asInstanceOf[String]
            logger.info("Evaluating %s named frame on asset %s, obtained url '%s'".format(fc.name, asset.asset.tag, url))
            ViewSpecs(fc.name, fc.title.get, fc.style, true, url)
          } else {
            logger.info("Evaluating %s named frame on asset %s, not enabled".format(fc.name, asset.asset.tag))
            ViewSpecs(fc.name, fc.title.get)
          }
        } else {
          logger.info("Frame config was disabled, skipping evaluation")
          ViewSpecs(fc.name, fc.title.get)
        }
      })
    }.filter(_.enabled)
  }
}