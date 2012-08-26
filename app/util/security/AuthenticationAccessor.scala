package util
package security

trait AuthenticationAccessor {
  def getAuthentication(): AuthenticationProvider
}
