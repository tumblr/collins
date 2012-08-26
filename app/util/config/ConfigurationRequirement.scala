package util
package config

sealed abstract class ConfigurationRequirement

object ConfigValue {
  object Required extends ConfigurationRequirement
  object Optional extends ConfigurationRequirement
}
