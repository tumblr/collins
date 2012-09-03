package util
package config

trait ConfigSource { self: ConfigAccessor =>
  val source: TypesafeConfiguration

  implicit val configValue = ConfigValue.Optional

  override def underlying = Some(source)
  override def underlying_=(config: Option[TypesafeConfiguration]) {
  }
}
