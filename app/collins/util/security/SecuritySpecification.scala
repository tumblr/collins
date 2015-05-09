package collins.util.security

import scala.annotation.implicitNotFound

@implicitNotFound(msg = "Didn't find an implicit SecuritySpecification but expected one")
trait SecuritySpecification {
  val isSecure: Boolean
  val requiredCredentials: Set[String]
  val securityConcern: String

  def requiresAuthorization: Boolean = requiredCredentials.nonEmpty
}
