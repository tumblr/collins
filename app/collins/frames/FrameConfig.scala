package collins.frames

import collins.util.config.ConfigAccessor
import collins.util.config.ConfigSource
import collins.util.config.ConfigValue
import collins.util.config.Configurable
import collins.util.config.TypesafeConfiguration

case class FrameConfig(val name: String, override val source: TypesafeConfiguration)
    extends ConfigAccessor with ConfigSource {
  def enabled = getBoolean("enabled", false)
  def title = getString("title")(ConfigValue.Required)
  def style = getString("style", "width: 100%;height: 1200px;")
  def script = getString("script", """
    function isEnabled(asset) { 
      return false; 
    }
    
    function getUrl(asset) { 
      return "";
    } 
    """)
  def urlScript = getString("urlScript", """function(asset) { return ""} """)
  
  def validateConfig() {
    if (enabled) {
      title
    }
  }
}

object ViewsConfig extends Configurable {
  override val namespace = "views"
  override val referenceConfigFilename = "views_reference.conf"

  def enabled = getBoolean("enabled", false)
  def frames: List[FrameConfig] = getObjectMap("frames").toList.map {
    case (name, o) =>
      FrameConfig(name, o.toConfig)
  }

  override def validateConfig() {
    if (enabled) {
      frames.foreach { _.validateConfig() }
    }
  }
}

