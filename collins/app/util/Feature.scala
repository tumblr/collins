package util

/**
 * Basic helpers for configuration based feature flags
 */

object Feature {
  object Messages extends MessageHelper("feature") {
    def ignoreDangerousCommands(s: String) = message("ignoreDangerousCommands", s)
  }

  // This exists for backwards compatibility with an old version of the Feature class
  def apply(
    name: String,
    verified: Boolean = false,
    source: Map[String, String] = Map.empty
  ): Feature = {
    FeatureConfigValue("features", name, verified, source)
  }
}

// Mixin for features with a common configuration key
trait FeatureConfigI {
  val rootKey: String
  val verified: Boolean
  val source: Map[String,String]

  def feature(name: String) = FeatureConfigValue(rootKey, name, verified, source)
}
// For users who won't touch verified/source stuff
trait FeatureConfigSkinny extends FeatureConfigI {
  override val verified = false
  override val source = Map.empty[String,String]
}

// Nicer on the eyes than a trait with overrides, less code too
abstract class FeatureConfig(
  override val rootKey: String,
  override val verified: Boolean = false,
  override val source: Map[String, String] = Map.empty
) extends FeatureConfigI

// Represents a value from a config, using key and name to resolve
case class FeatureConfigValue(
  override val rootKey: String,
  override val name: String,
  override var _verified: Boolean,
  override val source: Map[String,String]
) extends Feature

// Actual worker code, provides a set of methods helpful when dealing with feature flags (e.g.
// isSet, isEnabled, etc)
trait Feature extends Config {

  // A configuration setting is of the form rootKey.name
  val rootKey: String // this is the parent key (e.g. features)
  val name: String // this is the name of the feature (e.g. enableX)

  // This exists for backwards compatibility with when Feature was a case class, and verified was
  // copied
  protected var _verified: Boolean
  def verified: Boolean = _verified
  protected def verified_= (value: Boolean) = _verified = value

  def isSet: Boolean = get(rootKey).map(_.keys.contains(name)).getOrElse(false)
  def isUnset: Boolean = !isSet
  def enabled: Boolean = isSet && getBoolean(rootKey, name).getOrElse(false)
  def disabled: Boolean = !enabled

  def whenEnabled(f: => Unit) = when(_.enabled)(f)
  def whenEnabledOrUnset(f: => Unit) = when(f => f.enabled || f.isUnset)(f)
  def whenDisabledOrUnset(f: => Unit) = when(f => f.disabled || f.isUnset)(f)
  def whenSet(f: => Unit) = when(_.isSet)(f)

  def ifEnabled[T](f: Feature => T): Option[T] = if(enabled) {
    Some(f(this))
  } else {
    None
  }
  def ifSet[T](f: Feature => T): Option[T] = if(isSet) {
    Some(f(this))
  } else {
    None
  }

  def when(f: Feature => Boolean): Feature = {
    if (f(this)) {
      this.verified = true
    } else {
      this.verified = false
    }
    this
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

  def getString(default: String): String = getString(rootKey, name, default)

  def toInt(default: Int) = getInt(rootKey, name).getOrElse(default)

  def toSet(upcase: Boolean = true): Set[String] = getStringSet(rootKey, name, None, upcase)
  def toSet: Set[String] = toSet()
}
