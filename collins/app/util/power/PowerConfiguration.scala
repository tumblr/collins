package util
package power

case class PowerConfiguration(
  unitsRequired: Int,
  useAlphabeticNames: Boolean,
  private val _components: Set[String]
) extends MessageHelper("powerconfiguration") {
  import PowerConfiguration.Messages._

  require(unitsRequired >= 0 && unitsRequired <= 18, UnitsRequired)
  if (unitsRequired > 0) {
    val (empty, nonEmpty) = _components.partition(_.trim.isEmpty)
    require(empty.size == 0, ComponentsUnspecified)
    val (trimmed, ok) = _components.partition(s => s.trim != s)
    require(trimmed.size == 0, ComponentInvalid(trimmed.head))
  }

  val components: Set[Symbol] = _components.map(s => Symbol(s.toUpperCase))
  lazy val units: PowerUnits =
    PowerUnits((0 until unitsRequired).map(id => PowerUnit(this, id)))

  def hasComponent(component: Symbol) = components.contains(Symbol(component.name.toUpperCase))
}

object PowerConfiguration extends FeatureConfig("powerconfiguration") {

  val DefaultUnitsRequired = feature("unitsRequired").ifSet { f =>
    f.toInt(-1)
  }.filter(i => i >= 0 && i <= 18).getOrElse(1)
  val DefaultUseAlphabeticNames = feature("useAlphabeticNames").ifSet(_.enabled).getOrElse(true)
  val DefaultComponents = feature("unitComponents").toSet

  object Messages extends MessageHelper("powerconfiguration") {
    def ComponentInvalid(ct: String) =
      messageWithDefault("unitComponents.invalid",
                         "Specified unitComponent %s is invalid".format(ct), ct)
    def ComponentLabel(ct: String, idx: String) =
      messageWithDefault(labelFor(ct), defaultLabel(ct, idx), idx)
    val ComponentsUnspecified =
      messageWithDefault("unitComponents.unspecified", "unitComponents not specified but required")
    val UnitsRequired =
      messageWithDefault("unitsRequired.range", "unitsRequired must be >= 0 && <= 18")
    def MissingData(key: String, label: String) =
      messageWithDefault("missingData", defaultMissing(key, label), key, label)

    private def labelFor(ct: String) = "unit.%s.label".format(ct.toLowerCase)
    private def defaultLabel(ct: String, idx: String) = "%s %s".format(ct, idx)
    private def defaultMissing(k: String, l: String) =
      "Did not find value for %s, required for %s".format(k, l)
  }

  def apply(): PowerConfiguration = {
    new PowerConfiguration(
      DefaultUnitsRequired, DefaultUseAlphabeticNames, DefaultComponents
    )
  }

  def apply(cfg: Option[PowerConfiguration]): PowerConfiguration = {
    cfg.getOrElse(apply())
  }
}
