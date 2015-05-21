package collins.util.security

case class SecuritySpec(
  isSecure: Boolean,
  requiredCredentials: Set[String],
  securityConcern: String = SecuritySpec.LegacyMarker
) extends SecuritySpecification {
  def this(secure: Boolean, creds: Seq[String]) = this(secure, creds.toSet, SecuritySpec.LegacyMarker)
}

object SecuritySpec {
  val LegacyMarker = "SecuritySpec Version 1.1"
  def apply(secure: Boolean, creds: Seq[String]) =
    new SecuritySpec(secure, creds.toSet)
  def apply(isSecure: Boolean) = new SecuritySpec(isSecure, Set[String]())
  def fromConfig(concern: String, default: SecuritySpecification): SecuritySpecification = {
    AuthenticationProvider.permissions(concern) match {
      case None =>
        SecuritySpec(default.isSecure, default.requiredCredentials, default.securityConcern)
      case Some(set) =>
        new SecuritySpec(true, set, concern)
    }
  }
}
