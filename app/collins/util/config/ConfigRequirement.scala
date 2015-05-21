package collins.util.config

sealed abstract class ConfigRequirement

object ConfigValue {
  object Required extends ConfigRequirement
  object Optional extends ConfigRequirement
}
