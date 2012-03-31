package util

case class Feature(name: String, verified: Boolean = false, override val source: Map[String,String] = Map.empty) extends Config {
  val Key = "features"

  def isSet: Boolean = getBoolean(Key, name).map(_ => true).getOrElse(false)
  def isUnset: Boolean = !isSet
  def enabled: Boolean = isSet && getBoolean(Key, name).getOrElse(false)
  def disabled: Boolean = !enabled

  def whenEnabled(f: => Unit) = when(_.enabled)(f)
  def whenEnabledOrUnset(f: => Unit) = when(f => f.enabled || f.isUnset)(f)
  def whenDisabledOrUnset(f: => Unit) = when(f => f.disabled || f.isUnset)(f)
  def whenSet(f: => Unit) = when(_.isSet)(f)

  def when(f: Feature => Boolean): Feature = {
    if (f(this)) {
      this.copy(verified = true)
    } else {
      this.copy(verified = false)
    }
  }
  def apply(f: => Unit): Feature = {
    if (verified) { f }
    this
  }
  def orElse(f: => Unit): Feature = {
    if (!verified) { f }
    this
  }

  def toBoolean(default: Boolean) = if (isSet) {
    enabled
  } else {
    default
  }

  def toSet(upcase: Boolean = true): Set[String] = getStringSet(Key, name, None, upcase)
  def toSet: Set[String] = toSet()
}
